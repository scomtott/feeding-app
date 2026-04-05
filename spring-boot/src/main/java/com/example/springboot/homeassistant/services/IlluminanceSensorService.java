package com.example.springboot.homeassistant.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.springboot.homeassistant.models.SensorEntity;
import com.example.springboot.homeassistant.websocket.messages.HaWsStateChangedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class IlluminanceSensorService {

    private static final long PERIOD_MILLIS = 5 * 60 * 1000L;

    private final BathroomTelemetryStorageService bathroomTelemetryStorageService;
    private final Object lock = new Object();
    private final NavigableMap<Long, PeriodBuffer> buffersByPeriod = new TreeMap<>();

    @Async("telemetryExecutor")
    public void handleIlluminanceSensorStateChanged(HaWsStateChangedEvent<SensorEntity> event) {
        if (event.event() == null || event.event().data() == null) {
            return;
        }

        SensorEntity newState = event.event().data().newState();

        if (newState == null) {
            return;
        }

        String entityId = newState.entityId();
        if (entityId == null || !bathroomTelemetryStorageService.isTrackedIlluminanceSensor(entityId)) {
            return;
        }

        Double illuminance = newState.getNumericalValue();
        if (illuminance == null) {
            return;
        }

        Instant sampleTs = HomeAssistantEventUtils.parseEventTimestamp(newState.lastUpdated(), newState.lastChanged());

        synchronized (lock) {
            long period = periodIndexFor(sampleTs);
            PeriodBuffer buffer = getOrCreateBuffer(period);
            buffer.samples.add(new IlluminanceSample(sampleTs, illuminance));
            if (log.isDebugEnabled()) {
                log.debug(
                    "Illuminance sample buffered: entity={} ts={} lux={} period={} periodStart={} periodEnd={} sampleCount={}",
                    entityId,
                    sampleTs,
                    illuminance,
                    period,
                    periodStart(period),
                    periodEnd(period),
                    buffer.samples.size()
                );
            }
            processBuffers(Instant.now());
        }
    }

    public void notifyContextStateChange(Instant eventTs) {
        Instant ts = eventTs == null ? Instant.now() : eventTs;
        synchronized (lock) {
            long currentPeriod = periodIndexFor(ts);
            getOrCreateBuffer(currentPeriod).contextTriggered = true;
            getOrCreateBuffer(currentPeriod + 1).forceRawFlush = true;
            if (log.isDebugEnabled()) {
                log.debug(
                    "Context trigger received: ts={} currentPeriod={} prevPeriod={} nextPeriod={} (next forced raw)",
                    ts,
                    currentPeriod,
                    currentPeriod - 1,
                    currentPeriod + 1
                );
            }
            processBuffers(Instant.now());
        }
    }

    @Scheduled(fixedRate = 10000)
    public void finalizeIlluminanceBuffers() {
        synchronized (lock) {
            processBuffers(Instant.now());
        }
    }

    private void processBuffers(Instant now) {
        long currentPeriod = periodIndexFor(now);
        if (log.isDebugEnabled()) {
            log.debug("Processing illuminance buffers: now={} currentPeriod={} trackedPeriods={}", now, currentPeriod, buffersByPeriod.size());
        }

        // Raw flush pass for ended periods so triggered windows are emitted at period end.
        for (Long period : new ArrayList<>(buffersByPeriod.headMap(currentPeriod, true).keySet())) {
            if (period >= currentPeriod) {
                continue;
            }

            PeriodBuffer current = buffersByPeriod.get(period);
            if (current == null || current.finalized) {
                continue;
            }

            PeriodBuffer previous = buffersByPeriod.get(period - 1);
            boolean shouldFlushRawNow = current.contextTriggered
                || current.forceRawFlush
                || (previous != null && previous.contextTriggered);

            if (shouldFlushRawNow) {
                if (log.isDebugEnabled()) {
                    log.debug(
                        "Raw flush pass: period={} samples={} contextTriggered={} forceRawFlush={} prevTriggered={}",
                        period,
                        current.samples.size(),
                        current.contextTriggered,
                        current.forceRawFlush,
                        previous != null && previous.contextTriggered
                    );
                }
                persistRawSamples(current);
            }
        }

        // Finalization pass with one-period lookahead for previous/current/next trigger rule.
        long maxFinalizablePeriod = currentPeriod - 2;
        for (Long period : new ArrayList<>(buffersByPeriod.headMap(maxFinalizablePeriod + 1, true).keySet())) {
            PeriodBuffer current = buffersByPeriod.get(period);
            if (current == null || current.finalized) {
                continue;
            }

            PeriodBuffer previous = buffersByPeriod.get(period - 1);
            PeriodBuffer next = buffersByPeriod.get(period + 1);

            boolean rawRequired = current.contextTriggered
                || current.forceRawFlush
                || (previous != null && previous.contextTriggered)
                || (next != null && next.contextTriggered)
                || current.hasAnyRawPersisted();

            if (log.isDebugEnabled()) {
                log.debug(
                    "Finalizing period={} samples={} rawRequired={} contextTriggered={} forceRawFlush={} prevTriggered={} nextTriggered={} hasRawPersisted={}",
                    period,
                    current.samples.size(),
                    rawRequired,
                    current.contextTriggered,
                    current.forceRawFlush,
                    previous != null && previous.contextTriggered,
                    next != null && next.contextTriggered,
                    current.hasAnyRawPersisted()
                );
            }

            if (rawRequired) {
                persistRawSamples(current);
            } else {
                persistAggregate(current);
            }

            current.finalized = true;
        }

        // Keep only recent periods to prevent unbounded memory growth.
        long oldestToKeep = currentPeriod - 4;
        int beforeEvict = buffersByPeriod.size();
        buffersByPeriod.headMap(oldestToKeep, false).entrySet().removeIf(entry -> entry.getValue().finalized);
        int evicted = beforeEvict - buffersByPeriod.size();
        if (log.isDebugEnabled() && evicted > 0) {
            log.debug("Evicted {} finalized illuminance periods older than {}", evicted, oldestToKeep);
        }
    }

    private void persistRawSamples(PeriodBuffer buffer) {
        int persistedCount = 0;
        for (IlluminanceSample sample : buffer.samples) {
            if (sample.persistedRaw) {
                continue;
            }

            bathroomTelemetryStorageService.storeIlluminanceEvent(
                sample.illuminanceLux,
                sample.tsUtc,
                "illuminance-buffer-raw",
                null
            );
            sample.persistedRaw = true;
            persistedCount++;
        }

        if (log.isDebugEnabled() && persistedCount > 0) {
            log.debug("Persisted {} raw illuminance samples", persistedCount);
        }
    }

    private void persistAggregate(PeriodBuffer buffer) {
        if (buffer.aggregatePersisted || buffer.samples.isEmpty()) {
            return;
        }

        double sum = 0.0;
        List<IlluminanceSample> ordered = new ArrayList<>(buffer.samples);
        ordered.sort(Comparator.comparing(sample -> sample.tsUtc));

        for (IlluminanceSample sample : ordered) {
            sum += sample.illuminanceLux;
        }

        double avg = sum / ordered.size();
        Instant medianTs = ordered.get(ordered.size() / 2).tsUtc;

        bathroomTelemetryStorageService.storeIlluminanceEvent(
            avg,
            medianTs,
            "illuminance-buffer-avg-5m",
            null
        );
        buffer.aggregatePersisted = true;
        if (log.isDebugEnabled()) {
            log.debug("Persisted aggregate illuminance sample: count={} avg={} medianTs={}", ordered.size(), avg, medianTs);
        }
    }

    private PeriodBuffer getOrCreateBuffer(long periodIndex) {
        return buffersByPeriod.computeIfAbsent(periodIndex, ignored -> new PeriodBuffer());
    }

    private long periodIndexFor(Instant ts) {
        return Math.floorDiv(ts.toEpochMilli(), PERIOD_MILLIS);
    }

    private Instant periodStart(long periodIndex) {
        return Instant.ofEpochMilli(periodIndex * PERIOD_MILLIS);
    }

    private Instant periodEnd(long periodIndex) {
        return Instant.ofEpochMilli((periodIndex + 1) * PERIOD_MILLIS);
    }

    private static final class PeriodBuffer {
        private final List<IlluminanceSample> samples = new ArrayList<>();
        private boolean contextTriggered;
        private boolean forceRawFlush;
        private boolean aggregatePersisted;
        private boolean finalized;

        private boolean hasAnyRawPersisted() {
            return samples.stream().anyMatch(sample -> sample.persistedRaw);
        }
    }

    private static final class IlluminanceSample {
        private final Instant tsUtc;
        private final double illuminanceLux;
        private boolean persistedRaw;

        private IlluminanceSample(Instant tsUtc, double illuminanceLux) {
            this.tsUtc = tsUtc;
            this.illuminanceLux = illuminanceLux;
        }
    }
}

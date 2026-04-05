package com.example.springboot.homeassistant.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.springboot.homeassistant.persistence.BathroomTelemetryEventRepository;
import com.example.springboot.homeassistant.persistence.BathroomTimelinePointRepository;
import com.example.springboot.homeassistant.persistence.entities.BathroomTelemetryEvent;
import com.example.springboot.homeassistant.persistence.entities.BathroomTimelinePoint;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/homeassistant/telemetry")
@RequiredArgsConstructor
public class BathroomTelemetryController {

    private static final Duration DEFAULT_LOOKBACK = Duration.ofHours(24);

    private final BathroomTelemetryEventRepository eventRepository;
    private final BathroomTimelinePointRepository timelineRepository;

    @GetMapping("/events")
    public List<BathroomTelemetryEventDto> getEvents(
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        Instant toTs = parseOrDefault(to, Instant.now());
        Instant fromTs = parseOrDefault(from, toTs.minus(DEFAULT_LOOKBACK));

        List<BathroomTelemetryEvent> events = eventRepository.findByTsUtcBetweenOrderByTsUtcAsc(fromTs, toTs);
        if (limit != null && limit > 0 && events.size() > limit) {
            events = events.subList(events.size() - limit, events.size());
        }

        return events.stream().map(BathroomTelemetryEventDto::from).toList();
    }

    @GetMapping("/timeline")
    public List<BathroomTimelinePointDto> getTimeline(
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        Instant toTs = parseOrDefault(to, Instant.now());
        Instant fromTs = parseOrDefault(from, toTs.minus(DEFAULT_LOOKBACK));

        List<BathroomTimelinePoint> points = timelineRepository.findByTsUtcBetweenOrderByTsUtcAsc(fromTs, toTs);
        if (limit != null && limit > 0 && points.size() > limit) {
            points = points.subList(points.size() - limit, points.size());
        }

        return points.stream().map(BathroomTimelinePointDto::from).toList();
    }

    private Instant parseOrDefault(String raw, Instant fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            return Instant.parse(raw);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public record BathroomTelemetryEventDto(
        Long id,
        Instant tsUtc,
        String entityId,
        String signalType,
        Boolean valueBool,
        Double valueNum,
        String valueText,
        String source
    ) {
        static BathroomTelemetryEventDto from(BathroomTelemetryEvent event) {
            return new BathroomTelemetryEventDto(
                event.getId(),
                event.getTsUtc(),
                event.getEntityId(),
                event.getSignalType() == null ? null : event.getSignalType().name(),
                event.getValueBool(),
                event.getValueNum(),
                event.getValueText(),
                event.getSource()
            );
        }
    }

    public record BathroomTimelinePointDto(
        Long id,
        Instant tsUtc,
        Boolean occupancy,
        Double illuminanceLux,
        Boolean lightBathroom1On,
        Boolean lightBathroom2On,
        Boolean lightGledoptoOn
    ) {
        static BathroomTimelinePointDto from(BathroomTimelinePoint point) {
            return new BathroomTimelinePointDto(
                point.getId(),
                point.getTsUtc(),
                point.getOccupancy(),
                point.getIlluminanceLux(),
                point.getLightBathroom1On(),
                point.getLightBathroom2On(),
                point.getLightGledoptoOn()
            );
        }
    }
}

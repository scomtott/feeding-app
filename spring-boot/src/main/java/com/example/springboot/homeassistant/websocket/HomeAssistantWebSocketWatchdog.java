package com.example.springboot.homeassistant.websocket;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.WebSocketConnectionManager;

import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the Home Assistant websocket subscription alive.
 *
 * <p>Without this the integration dies silently and stays dead: the socket drops, nothing
 * reconnects, and every Home Assistant sourced feature stops with no error anywhere. That is not
 * hypothetical - it ran that way for fifteen hours before anyone noticed the bathroom light had
 * stopped responding to motion.
 *
 * <p>There are two failure modes and this handles both:
 *
 * <ul>
 *   <li><b>A clean close</b> - an HA restart or a socket reset. The session goes away and fires the
 *       close callback, but nothing rebuilt it.</li>
 *   <li><b>A half-open socket</b> - a network blip can leave a connection that still reports
 *       {@code isOpen()} while delivering nothing. It fires no close callback, so anything that only
 *       asks "is it connected?" will happily report healthy forever. Only a ping/pong round trip
 *       catches it.</li>
 * </ul>
 *
 * <p>The check runs every {@value #CHECK_INTERVAL_SECONDS} seconds, so recovery takes at most that
 * long. That is comfortably inside the window where a bathroom light still feels automatic, and it
 * keeps the retry rate low if Home Assistant is down for a while.
 */
@Component
@Slf4j
public class HomeAssistantWebSocketWatchdog {

    static final int CHECK_INTERVAL_SECONDS = 30;

    private final HomeAssistantWebSocketHandler handler;
    private final WebSocketConnectionManager connectionManager;

    /** Guards against a slow reconnect overlapping the next tick and starting a second one. */
    private final AtomicBoolean reconnectInProgress = new AtomicBoolean();
    private final AtomicInteger failedReconnects = new AtomicInteger();

    public HomeAssistantWebSocketWatchdog(
        HomeAssistantWebSocketHandler handler,
        WebSocketConnectionManager homeAssistantWebSocketConnectionManager
    ) {
        this.handler = handler;
        this.connectionManager = homeAssistantWebSocketConnectionManager;
    }

    @Scheduled(fixedDelay = CHECK_INTERVAL_SECONDS, initialDelay = CHECK_INTERVAL_SECONDS, timeUnit = TimeUnit.SECONDS)
    public void check() {
        if (!handler.isConnected()) {
            reconnect("there is no open session");
            return;
        }

        if (!handler.isSubscribed()) {
            // Connected, but not receiving anything - the state we actually care about.
            reconnect("the session is open but not subscribed to state_changed");
            return;
        }

        failedReconnects.set(0);

        if (handler.isPingOutstanding()) {
            reconnect("the previous ping went unanswered, so the socket is half-open");
            return;
        }

        handler.sendPing();
    }

    private void reconnect(String reason) {
        if (!reconnectInProgress.compareAndSet(false, true)) {
            log.debug("Skipping a reconnect check while one is already running");
            return;
        }

        try {
            int attempt = failedReconnects.incrementAndGet();
            log.warn("Reconnecting the Home Assistant websocket (attempt {}): {}", attempt, reason);

            // stop() first, and this is load bearing. ConnectionManagerSupport.start() is a no-op
            // unless its `running` flag is clear, and a dropped socket never clears it - only
            // stop() does. Calling start() on its own would look like a reconnect and silently do
            // nothing, which is precisely the failure this class exists to prevent.
            connectionManager.stop();
            connectionManager.start();
        } catch (RuntimeException e) {
            log.error("Failed to reconnect the Home Assistant websocket", e);
        } finally {
            reconnectInProgress.set(false);
        }
    }
}

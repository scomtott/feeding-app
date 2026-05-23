package com.example.springboot.homeassistant.services;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface DelayedActionService {

    void scheduleTurnOffLight(String actionKey, String lightEntityId, Duration delay);

    void scheduleTurnOffLightVerification(String actionKey, String lightEntityId, Duration delay, int attempt, long rootScheduledAtEpochMs);

    void cancel(String actionKey);

    void cancelTurnOffLightActions(String lightEntityId);

    void cancelTurnOffLightActionsScheduledBefore(String lightEntityId, Instant cutoff);

    boolean isScheduled(String actionKey);

    List<PendingAction> getPendingActions();

    record PendingAction(
        String actionKey,
        String lightEntityId,
        String phase,
        int attempt,
        long rootScheduledAtEpochMs,
        Instant nextFireTime
    ) {
    }
}

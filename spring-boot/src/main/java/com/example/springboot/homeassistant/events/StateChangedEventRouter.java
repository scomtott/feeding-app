package com.example.springboot.homeassistant.events;

import java.util.List;

import org.springframework.context.ApplicationListener;

import lombok.extern.slf4j.Slf4j;

/**
 * Delivers {@link StateChangedEvent}s to the subscriptions wired in
 * {@code HomeAssistantEventSubscriptions}.
 *
 * <p><strong>Each subscription is isolated.</strong> A subscriber that throws is logged and
 * skipped, and the remaining subscribers still run. This is deliberate: an earlier arrangement ran
 * a domain's handlers inside a single try/catch, so the first failure silently suppressed every
 * later handler for that event.
 *
 * <p>Wired as a {@code @Bean} rather than component-scanned, so the subscription list is exactly
 * what the configuration declares.
 */
@Slf4j
public class StateChangedEventRouter implements ApplicationListener<StateChangedEvent> {

    private final List<StateChangedSubscription<?>> subscriptions;

    public StateChangedEventRouter(List<StateChangedSubscription<?>> subscriptions) {
        this.subscriptions = List.copyOf(subscriptions);
    }

    @Override
    public void onApplicationEvent(StateChangedEvent event) {
        for (StateChangedSubscription<?> subscription : subscriptions) {
            dispatch(subscription, event);
        }
    }

    private <E extends StateChangedEvent> void dispatch(StateChangedSubscription<E> subscription, StateChangedEvent event) {
        if (!subscription.eventType().isInstance(event)) {
            return;
        }

        E typedEvent = subscription.eventType().cast(event);
        try {
            if (!subscription.criteria().test(typedEvent)) {
                return;
            }
            subscription.handler().accept(typedEvent);
        } catch (Exception e) {
            log.warn(
                "Subscription '{}' failed handling {} event for {}",
                subscription.name(),
                event.domain(),
                event.entityId(),
                e
            );
        }
    }
}

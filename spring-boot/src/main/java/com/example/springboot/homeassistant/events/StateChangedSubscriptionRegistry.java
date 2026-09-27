package com.example.springboot.homeassistant.events;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Collects subscriptions for {@link StateChangedEventRouter}. Used only by the wiring
 * {@code @Configuration}; there is no runtime registration, no discovery, and no plugin surface.
 *
 * <p>A change to the set of subscriptions is therefore a code change and a redeploy.
 */
public final class StateChangedSubscriptionRegistry {

    private final List<StateChangedSubscription<?>> subscriptions = new ArrayList<>();

    public <E extends StateChangedEvent> StateChangedSubscriptionRegistry subscribe(
        Class<E> eventType,
        Predicate<E> criteria,
        Consumer<E> handler,
        int order,
        String name
    ) {
        subscriptions.add(new StateChangedSubscription<>(
            Objects.requireNonNull(eventType, "eventType"),
            Objects.requireNonNull(criteria, "criteria"),
            Objects.requireNonNull(handler, "handler"),
            order,
            Objects.requireNonNull(name, "name")
        ));
        return this;
    }

    /**
     * Returns the subscriptions sorted by {@code (order, name)}. Sorting explicitly rather than
     * trusting declaration order means the dispatch sequence cannot drift with classpath or scan
     * order.
     */
    public List<StateChangedSubscription<?>> build() {
        List<StateChangedSubscription<?>> ordered = new ArrayList<>(subscriptions);
        ordered.sort(Comparator
            .comparingInt((StateChangedSubscription<?> subscription) -> subscription.order())
            .thenComparing(subscription -> subscription.name()));
        return List.copyOf(ordered);
    }
}

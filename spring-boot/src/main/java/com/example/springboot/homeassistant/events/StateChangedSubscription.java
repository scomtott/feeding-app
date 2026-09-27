package com.example.springboot.homeassistant.events;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One subscriber to a {@link StateChangedEvent}: the event type it wants, the further criteria it
 * filters on, the handler to run, and the order it should run in.
 *
 * @param eventType the concrete event type this subscriber handles
 * @param criteria  further filtering, evaluated only once the type has matched
 * @param handler   invoked when both the type and the criteria match
 * @param order     dispatch order, ascending
 * @param name      used in failure logs so a broken subscriber is identifiable
 */
public record StateChangedSubscription<E extends StateChangedEvent>(
    Class<E> eventType,
    Predicate<E> criteria,
    Consumer<E> handler,
    int order,
    String name
) {
}

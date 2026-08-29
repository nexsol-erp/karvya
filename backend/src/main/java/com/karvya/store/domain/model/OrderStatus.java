package com.karvya.store.domain.model;

import java.util.Set;

/**
 * Where an order has got to in fulfilment.
 *
 * <p>Deliberately separate from {@link PaymentStatus}. An order can be packed
 * and shipped before an offline payment clears, or paid the moment it is placed
 * and sit unshipped for a week; folding the two into one enum would force a
 * false ordering between them.
 *
 * <p>Transitions are declared here rather than checked ad hoc at each call
 * site, so there is one place to read what is legal.
 */
public enum OrderStatus {

    /** Placed by the customer, not yet looked at. Stock is already reserved. */
    NEW,

    /** The team has verified the order and intends to fulfil it. */
    CONFIRMED,

    /** Being packed. */
    PROCESSING,

    /** Handed to the courier. */
    SHIPPED,

    /** Complete. Terminal. */
    DELIVERED,

    /** Abandoned. Terminal, and the point at which stock is returned. */
    CANCELLED;

    private static final Set<OrderStatus> FROM_NEW = Set.of(CONFIRMED, CANCELLED);
    private static final Set<OrderStatus> FROM_CONFIRMED = Set.of(PROCESSING, CANCELLED);
    private static final Set<OrderStatus> FROM_PROCESSING = Set.of(SHIPPED, CANCELLED);
    private static final Set<OrderStatus> FROM_SHIPPED = Set.of(DELIVERED);

    /**
     * Note that SHIPPED cannot move to CANCELLED. Once it is with the courier,
     * cancelling would silently return stock that has physically left, so that
     * case is a return, which phase two models properly.
     */
    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case NEW -> FROM_NEW;
            case CONFIRMED -> FROM_CONFIRMED;
            case PROCESSING -> FROM_PROCESSING;
            case SHIPPED -> FROM_SHIPPED;
            case DELIVERED, CANCELLED -> Set.of();
        };
    }

    public boolean canTransitionTo(OrderStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }

    /** True while the order still holds reserved stock. */
    public boolean holdsStock() {
        return this != CANCELLED;
    }
}

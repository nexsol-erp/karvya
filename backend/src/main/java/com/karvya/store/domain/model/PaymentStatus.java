package com.karvya.store.domain.model;

import java.util.Set;

/**
 * Whether the money has arrived. Independent of {@link OrderStatus}.
 *
 * <p>Phase one settles payment offline, so these states describe a
 * conversation with the customer rather than a gateway callback.
 */
public enum PaymentStatus {

    /** Order placed; nothing arranged yet. */
    PENDING,

    /** Instructions have been sent and the team is waiting. */
    AWAITING_PAYMENT,

    /** Received and recorded against the order. */
    PAID_OFFLINE,

    /** Returned to the customer. Terminal. */
    REFUNDED;

    public Set<PaymentStatus> allowedNext() {
        return switch (this) {
            case PENDING -> Set.of(AWAITING_PAYMENT, PAID_OFFLINE);
            case AWAITING_PAYMENT -> Set.of(PAID_OFFLINE);
            case PAID_OFFLINE -> Set.of(REFUNDED);
            case REFUNDED -> Set.of();
        };
    }

    public boolean canTransitionTo(PaymentStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isSettled() {
        return this == PAID_OFFLINE;
    }
}

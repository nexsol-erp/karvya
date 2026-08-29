package com.karvya.store.domain.model;

public enum NotificationStatus {
    /** Queued and due to be attempted. */
    PENDING,
    /** Delivered to the mail server. */
    SENT,
    /** Gave up after exhausting the retry budget. Needs a human. */
    FAILED
}

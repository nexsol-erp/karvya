package com.karvya.store.application.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drains the email outbox on a schedule.
 *
 * <p>Each pass claims a batch in one short transaction, sends with no
 * transaction open - so a slow mail server never holds a database lock - and
 * records each outcome separately. All three steps live on
 * {@link NotificationDispatcher} so they actually go through the transactional
 * proxy.
 *
 * <p>Switched off with {@code app.notifications.enabled=false}, which is how
 * tests drive it deliberately instead of racing a background timer.
 */
@Component
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);

    private final NotificationDispatcher dispatcher;

    public NotificationWorker(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${app.notifications.poll-interval:PT1M}")
    public void dispatchDue() {
        List<Long> claimed = dispatcher.claimBatch();
        if (claimed.isEmpty()) {
            return;
        }

        int sent = 0;
        for (Long id : claimed) {
            if (dispatcher.attempt(id)) {
                sent++;
            }
        }

        if (sent < claimed.size()) {
            log.info("Notification pass: {} sent, {} failed and will be retried",
                    sent, claimed.size() - sent);
        } else {
            log.debug("Notification pass: {} sent", sent);
        }
    }
}

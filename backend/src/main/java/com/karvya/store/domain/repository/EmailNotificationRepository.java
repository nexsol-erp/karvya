package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.EmailNotification;
import com.karvya.store.domain.model.NotificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.Instant;
import java.util.List;

public interface EmailNotificationRepository extends JpaRepository<EmailNotification, Long> {

    /**
     * Claims a batch of due notifications for sending.
     *
     * <p>Locked {@code FOR UPDATE SKIP LOCKED} so that two application
     * instances can run the worker at once: each takes rows the other has not
     * claimed instead of blocking or double-sending. The partial index on
     * {@code next_attempt_at WHERE status = 'PENDING'} means this touches only
     * what is actually due.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select n from EmailNotification n
             where n.status = com.karvya.store.domain.model.NotificationStatus.PENDING
               and n.nextAttemptAt <= :now
             order by n.nextAttemptAt asc
            """)
    List<EmailNotification> claimDue(Instant now, Pageable pageable);

    long countByStatus(NotificationStatus status);

    List<EmailNotification> findTop20ByStatusOrderByLastAttemptAtDesc(NotificationStatus status);
}

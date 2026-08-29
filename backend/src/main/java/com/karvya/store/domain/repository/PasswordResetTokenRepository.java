package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Invalidates every outstanding grant for a user. Called when a new one is
     * issued and again after a successful reset, so a token cannot be replayed
     * and only the most recent email ever works.
     */
    @Modifying
    @Query("""
            update PasswordResetToken t
               set t.usedAt = :now
             where t.user.id = :userId
               and t.usedAt is null
            """)
    int invalidateOutstanding(Long userId, Instant now);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(Instant cutoff);
}

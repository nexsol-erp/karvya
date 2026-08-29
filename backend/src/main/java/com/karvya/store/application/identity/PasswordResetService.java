package com.karvya.store.application.identity;

import com.karvya.store.domain.ConflictException;
import com.karvya.store.domain.model.AppUser;
import com.karvya.store.domain.model.EmailNotification;
import com.karvya.store.domain.model.PasswordResetToken;
import com.karvya.store.domain.repository.AppUserRepository;
import com.karvya.store.domain.repository.EmailNotificationRepository;
import com.karvya.store.domain.repository.PasswordResetTokenRepository;
import com.karvya.store.infrastructure.config.AppProperties;
import com.karvya.store.infrastructure.security.SecureTokens;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * The forgot-password and reset flow.
 *
 * <p>Three properties hold throughout. Requesting a reset always reports the
 * same thing whether or not the account exists. The token is stored only as a
 * hash, so the database cannot be used to reset anyone. And the email is
 * queued in the outbox inside the same transaction that issues the token, so
 * an unreachable mail server cannot leave a grant with no way to deliver it.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final AppUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final EmailNotificationRepository notifications;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public PasswordResetService(AppUserRepository users, PasswordResetTokenRepository tokens,
                                EmailNotificationRepository notifications, PasswordEncoder passwordEncoder,
                                AppProperties properties, ObjectMapper objectMapper) {
        this.users = users;
        this.tokens = tokens;
        this.notifications = notifications;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Issues a reset grant when the address belongs to an enabled account, and
     * does nothing at all otherwise. The caller reports success either way.
     */
    @Transactional
    public void requestReset(String email, String requestIp) {
        Optional<AppUser> found = users.findByEmailNormalized(AppUser.normalizeEmail(email));

        if (found.isEmpty()) {
            log.debug("Password reset requested for an unknown address");
            return;
        }

        AppUser user = found.get();
        if (!user.isEnabled()) {
            log.debug("Password reset requested for a disabled account {}", user.getId());
            return;
        }

        // any earlier grant stops working the moment a new one is issued
        tokens.invalidateOutstanding(user.getId(), Instant.now());

        String rawToken = SecureTokens.generate();
        Instant expiresAt = Instant.now().plus(properties.security().passwordResetTtl());

        tokens.save(PasswordResetToken.issue(user, SecureTokens.hash(rawToken), expiresAt, requestIp));

        notifications.save(EmailNotification.queue(
                EmailNotification.TYPE_PASSWORD_RESET,
                user.getEmail(),
                "Reset your password",
                payload(Map.of(
                        "fullName", user.getFullName(),
                        "resetUrl", properties.baseUrl() + "/reset-password?token=" + rawToken,
                        "expiresAt", expiresAt.toString()))));

        log.info("Password reset issued for account {}", user.getId());
    }

    /**
     * Consumes a grant and sets the new password.
     *
     * <p>An unknown, expired or already-used token all produce the same
     * failure, so the response cannot be used to learn which tokens exist.
     */
    @Transactional
    public Long completeReset(String rawToken, String newPassword) {
        PasswordResetToken token = tokens.findByTokenHash(SecureTokens.hash(rawToken))
                .filter(PasswordResetToken::isUsable)
                .orElseThrow(() -> new ConflictException("invalid-reset-token",
                        "That reset link is no longer valid. Please request a new one."));

        AppUser user = token.getUser();
        if (!user.isEnabled()) {
            throw new ConflictException("invalid-reset-token",
                    "That reset link is no longer valid. Please request a new one.");
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        user.setUpdatedBy("password-reset");

        token.markUsed();
        // belt and braces: any sibling grant issued in the same window dies too
        tokens.invalidateOutstanding(user.getId(), Instant.now());

        log.info("Password reset completed for account {}", user.getId());
        return user.getId();
    }

    private String payload(Map<String, String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise notification payload", e);
        }
    }
}

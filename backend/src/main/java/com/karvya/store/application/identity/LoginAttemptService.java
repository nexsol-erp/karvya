package com.karvya.store.application.identity;

import com.karvya.store.domain.model.AppUser;
import com.karvya.store.domain.repository.AppUserRepository;
import com.karvya.store.infrastructure.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the outcome of sign-in attempts against the account.
 *
 * <p>A separate bean on purpose. Calling a {@code @Transactional} method from
 * another method of the same class bypasses the proxy entirely, so these would
 * run outside a transaction and the counter would never be written - a failure
 * that is invisible until someone tries to brute-force an account.
 *
 * <p>{@code REQUIRES_NEW} so that recording a failure commits on its own. The
 * surrounding request is about to return 401, and the lockout counter must
 * survive that regardless.
 */
@Service
public class LoginAttemptService {

    private final AppUserRepository users;
    private final AppProperties properties;

    public LoginAttemptService(AppUserRepository users, AppProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String normalizedEmail) {
        users.findByEmailNormalized(normalizedEmail).ifPresent(AppUser::recordSuccessfulLogin);
    }

    /**
     * Counts a failure, locking the account once the limit is reached. Silently
     * does nothing for an unknown address, which keeps the timing and the
     * response identical either way.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String normalizedEmail) {
        users.findByEmailNormalized(normalizedEmail).ifPresent(user -> user.recordFailedLogin(
                properties.security().loginMaxAttempts(),
                properties.security().lockoutDuration()));
    }
}

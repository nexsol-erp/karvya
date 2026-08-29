package com.karvya.store.application.identity;

import com.karvya.store.application.identity.dto.CurrentUser;
import com.karvya.store.application.identity.dto.ProfileDtos;
import com.karvya.store.application.identity.dto.RegisterRequest;
import com.karvya.store.domain.ConflictException;
import com.karvya.store.domain.NotFoundException;
import com.karvya.store.domain.model.AppUser;
import com.karvya.store.domain.model.Role;
import com.karvya.store.domain.repository.AppUserRepository;
import com.karvya.store.domain.repository.RoleRepository;
import com.karvya.store.infrastructure.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/** Registration, profile and password changes for shopper accounts. */
@Service
public class CustomerAccountService {

    private static final Logger log = LoggerFactory.getLogger(CustomerAccountService.class);

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public CustomerAccountService(AppUserRepository users, RoleRepository roles,
                                  PasswordEncoder passwordEncoder, AppProperties properties) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    /**
     * Creates a shopper account.
     *
     * <p>A duplicate email is reported plainly. That does disclose whether an
     * address is registered, but the alternative - accepting the registration
     * silently and emailing the existing owner - is only worth its complexity
     * where accounts are themselves sensitive. Login and password reset, where
     * the disclosure actually helps an attacker, stay enumeration-resistant.
     *
     * <p>The unique constraint is caught as well as pre-checked: between the
     * check and the insert, a concurrent request can create the same address,
     * and the database is the only thing that can settle that race.
     */
    @Transactional
    public CurrentUser register(RegisterRequest request) {
        String normalized = AppUser.normalizeEmail(request.email());

        if (users.existsByEmailNormalized(normalized)) {
            throw new ConflictException("email-already-registered",
                    "An account already exists for that email address.");
        }

        String phone = (request.phone() == null || request.phone().isBlank()) ? null : request.phone().trim();
        if (phone != null && users.existsByPhone(phone)) {
            throw new ConflictException("phone-already-registered",
                    "An account already exists for that phone number.");
        }

        AppUser user = AppUser.create(
                request.email(),
                request.fullName(),
                passwordEncoder.encode(request.password()),
                phone);

        Role customerRole = roles.findByCode(Role.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role missing; reference data not loaded"));
        user.addRole(customerRole);

        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            log.debug("Registration lost a race on a unique constraint for {}", normalized);
            throw new ConflictException("email-already-registered",
                    "An account already exists for that email address.");
        }

        return toCurrentUser(user);
    }

    @Transactional(readOnly = true)
    public ProfileDtos.Response getProfile(Long userId) {
        return ProfileDtos.Response.from(requireUser(userId));
    }

    @Transactional
    public ProfileDtos.Response updateProfile(Long userId, ProfileDtos.Request request) {
        AppUser user = requireUser(userId);

        String phone = (request.phone() == null || request.phone().isBlank()) ? null : request.phone().trim();
        if (phone != null && !phone.equals(user.getPhone()) && users.existsByPhone(phone)) {
            throw new ConflictException("phone-already-registered",
                    "That phone number is already on another account.");
        }

        user.setFullName(request.fullName());
        user.setPhone(phone);
        user.setUpdatedBy(user.getEmailNormalized());
        return ProfileDtos.Response.from(user);
    }

    /**
     * Changes a password, requiring the current one.
     *
     * <p>Returns quietly on success; the caller is responsible for invalidating
     * other sessions, which is a web-layer concern rather than a domain one.
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        AppUser user = requireUser(userId);

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            // counted against the lockout budget so this cannot be used as an
            // oracle by someone who has hijacked a session
            user.recordFailedLogin(
                    properties.security().loginMaxAttempts(),
                    properties.security().lockoutDuration());
            throw new ConflictException("incorrect-password", "Your current password is not correct.");
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ConflictException("password-unchanged",
                    "Choose a password different from your current one.");
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        user.setUpdatedBy(user.getEmailNormalized());
    }

    @Transactional(readOnly = true)
    public CurrentUser currentUser(Long userId) {
        return toCurrentUser(requireUser(userId));
    }

    private AppUser requireUser(Long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Account", String.valueOf(userId)));
    }

    private CurrentUser toCurrentUser(AppUser user) {
        return new CurrentUser(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRoles().stream().map(Role::authority).sorted().toList(),
                user.isMustChangePassword());
    }

    /** Exposed for the login path, which needs the lockout policy. */
    public Duration lockoutDuration() {
        return properties.security().lockoutDuration();
    }

    public int loginMaxAttempts() {
        return properties.security().loginMaxAttempts();
    }
}

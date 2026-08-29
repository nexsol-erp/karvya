package com.karvya.store.infrastructure.config;

import com.karvya.store.domain.model.AppUser;
import com.karvya.store.domain.model.Role;
import com.karvya.store.domain.repository.AppUserRepository;
import com.karvya.store.domain.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator on first boot.
 *
 * <p>The password comes from the environment and is never a default: with
 * nothing configured, no account is created and the application says so. A
 * built-in fallback credential would be the single worst thing in the
 * codebase, since it would be identical on every deployment.
 *
 * <p>The account is created with {@code mustChangePassword} set, so the
 * bootstrap value stops working the moment a human first signs in.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public BootstrapAdminInitializer(AppUserRepository users, RoleRepository roles,
                                     PasswordEncoder passwordEncoder, AppProperties properties) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.BootstrapAdmin bootstrap = properties.bootstrapAdmin();

        // an administrator already exists: never touch it, and in particular
        // never reset its password back to the environment value
        if (users.countByRole(Role.ADMIN) > 0) {
            log.debug("An administrator already exists; bootstrap skipped");
            return;
        }

        if (bootstrap == null || !bootstrap.isConfigured()) {
            log.warn("No administrator exists and APP_ADMIN_PASSWORD is not set. "
                    + "Set APP_ADMIN_USERNAME, APP_ADMIN_EMAIL and APP_ADMIN_PASSWORD, then restart, "
                    + "to create the first administrator.");
            return;
        }

        String email = bootstrap.email();
        if (users.existsByEmailNormalized(AppUser.normalizeEmail(email))) {
            log.warn("Cannot bootstrap an administrator: {} is already registered as a customer. "
                    + "Use a different APP_ADMIN_EMAIL.", email);
            return;
        }

        Role adminRole = roles.findByCode(Role.ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "The ADMIN role is missing; reference data has not been loaded"));

        AppUser admin = AppUser.create(
                email,
                bootstrap.username(),
                passwordEncoder.encode(bootstrap.password()),
                null);
        admin.addRole(adminRole);
        admin.setMustChangePassword(true);
        admin.setUpdatedBy("bootstrap");

        users.save(admin);

        log.info("Created the first administrator ({}). It must change its password at first sign-in.",
                email);
    }
}

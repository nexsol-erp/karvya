package com.karvya.store.domain.model;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * An account. One table serves both shoppers and staff; what an account may do
 * is decided entirely by its roles.
 *
 * <p>Email is stored twice on purpose. {@code email} keeps whatever the person
 * typed, so correspondence uses their own capitalisation; {@code
 * emailNormalized} is the lower-cased form carrying the unique constraint, so
 * two accounts cannot differ by case alone.
 */
@Entity
@Table(name = "app_user")
public class AppUser extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "email_normalized", nullable = false, length = 255, unique = true)
    private String emailNormalized;

    @Column(length = 32)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected AppUser() {
    }

    public static AppUser create(String email, String fullName, String passwordHash, String phone) {
        AppUser user = new AppUser();
        user.email = email.trim();
        user.emailNormalized = normalizeEmail(email);
        user.fullName = fullName.trim();
        user.passwordHash = passwordHash;
        user.phone = (phone == null || phone.isBlank()) ? null : phone.trim();
        return user;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // ---- account state ----------------------------------------------------

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    /** True when the account can actually authenticate right now. */
    public boolean isAuthenticable() {
        return enabled && !isLocked();
    }

    public void recordSuccessfulLogin() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
    }

    /**
     * Records a failed attempt and locks the account once the limit is reached.
     * Locking is temporary by design: a permanent lock hands an attacker a
     * denial-of-service against any account whose email they know.
     */
    public void recordFailedLogin(int maxAttempts, Duration lockoutDuration) {
        this.failedAttempts++;
        if (this.failedAttempts >= maxAttempts) {
            this.lockedUntil = Instant.now().plus(lockoutDuration);
        }
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.mustChangePassword = false;
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    public boolean hasRole(String code) {
        return roles.stream().anyMatch(r -> r.getCode().equals(code));
    }

    public void addRole(Role role) {
        roles.add(role);
    }

    // ---- accessors --------------------------------------------------------

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getEmailNormalized() { return emailNormalized; }

    public void setEmail(String email) {
        this.email = email.trim();
        this.emailNormalized = normalizeEmail(email);
    }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = (phone == null || phone.isBlank()) ? null : phone.trim(); }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName.trim(); }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean v) { this.mustChangePassword = v; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public long getVersion() { return version; }
    public Set<Role> getRoles() { return roles; }
}

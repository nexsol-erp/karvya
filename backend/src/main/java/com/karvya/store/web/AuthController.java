package com.karvya.store.web;

import com.karvya.store.application.identity.CustomerAccountService;
import com.karvya.store.application.identity.PasswordResetService;
import com.karvya.store.application.identity.dto.CurrentUser;
import com.karvya.store.application.identity.dto.LoginRequest;
import com.karvya.store.application.identity.dto.PasswordRequests;
import com.karvya.store.application.identity.dto.RegisterRequest;
import com.karvya.store.domain.TooManyRequestsException;
import com.karvya.store.application.identity.LoginAttemptService;
import com.karvya.store.domain.model.AppUser;
import com.karvya.store.infrastructure.config.AppProperties;
import com.karvya.store.infrastructure.security.AppUserPrincipal;
import com.karvya.store.infrastructure.security.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * Authentication and password management.
 *
 * <p>Sessions are established explicitly rather than through a form-login
 * filter, because the client is a fetch call that wants JSON both ways. The
 * session id is rotated on every successful login, which is what prevents
 * session fixation: an id planted before authentication is discarded.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, sign-in and password management")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final Duration HOUR = Duration.ofHours(1);

    private final AuthenticationManager authenticationManager;
    private final CustomerAccountService accounts;
    private final PasswordResetService passwordResets;
    private final LoginAttemptService loginAttempts;
    private final RateLimiter rateLimiter;
    private final AppProperties properties;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, CustomerAccountService accounts,
                          PasswordResetService passwordResets, LoginAttemptService loginAttempts,
                          RateLimiter rateLimiter, AppProperties properties) {
        this.authenticationManager = authenticationManager;
        this.accounts = accounts;
        this.passwordResets = passwordResets;
        this.loginAttempts = loginAttempts;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a customer account")
    public CurrentUser register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        limit("register:" + clientIp(http), properties.security().registrationsPerHourPerIp(), HOUR);
        return accounts.register(request);
    }

    /**
     * Signs in.
     *
     * <p>Every failure returns the same 401 with the same wording, whether the
     * account is unknown, the password is wrong, the account is disabled or it
     * is temporarily locked. Distinguishing them would tell an attacker which
     * addresses are worth attacking.
     */
    @PostMapping("/login")
    @Operation(summary = "Sign in and start a session")
    public ResponseEntity<CurrentUser> login(@Valid @RequestBody LoginRequest request,
                                             HttpServletRequest http, HttpServletResponse response) {
        String ip = clientIp(http);
        String normalized = AppUser.normalizeEmail(request.email());

        limit("login:ip:" + ip, properties.security().loginAttemptsPerWindowPerIp(), LOGIN_WINDOW);

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalized, request.password()));

            // Rotate the session id to defeat fixation: an id planted before
            // sign-in stops being valid. changeSessionId keeps the session
            // object, unlike invalidate-and-recreate, so nothing already in it
            // (a pending cart merge, for instance) is silently dropped.
            http.getSession(true);
            http.changeSessionId();

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, http, response);

            loginAttempts.recordSuccess(normalized);
            rateLimiter.reset("login:ip:" + ip);

            AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
            return ResponseEntity.ok(accounts.currentUser(principal.getId()));

        } catch (AuthenticationException e) {
            loginAttempts.recordFailure(normalized);
            log.info("Failed sign-in attempt from {}", ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "End the current session")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        var session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in account")
    public CurrentUser me() {
        return accounts.currentUser(CurrentUserArgument.requireUserId());
    }

    /**
     * Changes the password, then ends the session so the browser must sign in
     * again with the new credential.
     */
    @PostMapping("/password/change")
    @Operation(summary = "Change your password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordRequests.ChangePassword request,
                                               HttpServletRequest http) {
        accounts.changePassword(CurrentUserArgument.requireUserId(),
                request.currentPassword(), request.newPassword());

        var session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    /**
     * Always returns 202, whether or not the address is registered. The
     * response is identical in both cases by design.
     */
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request a password reset link")
    public void forgotPassword(@Valid @RequestBody PasswordRequests.Forgot request, HttpServletRequest http) {
        limit("reset:" + clientIp(http), properties.security().passwordResetsPerHourPerIp(), HOUR);
        passwordResets.requestReset(request.email(), clientIp(http));
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set a new password using a reset token")
    public void resetPassword(@Valid @RequestBody PasswordRequests.Reset request, HttpServletRequest http) {
        limit("reset-complete:" + clientIp(http), properties.security().passwordResetsPerHourPerIp(), HOUR);
        passwordResets.completeReset(request.token(), request.newPassword());
    }

    // ---- helpers ----------------------------------------------------------

    private void limit(String key, int maxAttempts, Duration window) {
        if (!rateLimiter.tryAcquire(key, maxAttempts, window)) {
            throw new TooManyRequestsException();
        }
    }

    /**
     * The client address, honouring a single proxy hop. Nginx sets
     * X-Forwarded-For; anything beyond the first entry is client-supplied and
     * must not be trusted.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

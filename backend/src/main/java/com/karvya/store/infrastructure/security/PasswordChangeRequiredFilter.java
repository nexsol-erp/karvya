package com.karvya.store.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Blocks an account that must change its password from doing anything else.
 *
 * <p>Enforced here rather than in the interface, because the bootstrap
 * administrator's credential is known to whoever deployed the application and
 * may well be in a shell history or a CI log. Until it is replaced the account
 * can reach exactly two things: the endpoint that changes it, and the one that
 * says who it is.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALWAYS_ALLOWED = Set.of(
            "/api/v1/auth/password/change",
            "/api/v1/auth/logout",
            "/api/v1/auth/me");

    private final ObjectMapper objectMapper;

    public PasswordChangeRequiredFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof AppUserPrincipal principal
                && principal.isMustChangePassword()
                && request.getRequestURI().startsWith("/api/v1/")
                && !ALWAYS_ALLOWED.contains(request.getRequestURI())) {

            writeProblem(response, request.getRequestURI());
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * A problem document carrying a specific code, so the interface can route
     * to the change-password screen rather than showing a bare "forbidden".
     */
    private void writeProblem(HttpServletResponse response, String path) throws IOException {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "https://karvya.example/problems/password-change-required");
        problem.put("title", "Password change required");
        problem.put("status", HttpStatus.FORBIDDEN.value());
        problem.put("detail", "You must choose a new password before continuing.");
        problem.put("instance", path);
        problem.put("timestamp", Instant.now().toString());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}

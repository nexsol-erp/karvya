package com.karvya.store.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Forces the CSRF token to be materialised so its cookie is actually written.
 *
 * <p>Spring Security defers token generation until something reads it. For a
 * single-page app that means the XSRF-TOKEN cookie never appears on the first
 * GET, and the very first POST then fails with no obvious cause. Touching
 * {@link CsrfToken#getToken()} here resolves the deferred value and triggers
 * the cookie write.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
        filterChain.doFilter(request, response);
    }
}

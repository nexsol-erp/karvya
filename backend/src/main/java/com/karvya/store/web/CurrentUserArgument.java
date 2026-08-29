package com.karvya.store.web;

import com.karvya.store.infrastructure.security.AppUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the authenticated principal from the security context.
 *
 * <p>Controllers call this rather than trusting anything in the request, which
 * is what keeps a user id from being spoofable by a path variable or body
 * field. If there is no authenticated principal the filter chain has already
 * rejected the request, so returning null here would be a bug, not a case to
 * handle.
 */
public final class CurrentUserArgument {

    private CurrentUserArgument() {
    }

    public static AppUserPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new IllegalStateException("No authenticated principal on a protected endpoint");
        }
        return principal;
    }

    public static Long requireUserId() {
        return require().getId();
    }
}

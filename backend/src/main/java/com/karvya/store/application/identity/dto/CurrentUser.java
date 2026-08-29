package com.karvya.store.application.identity.dto;

import java.util.List;

/**
 * The signed-in account, as the client is allowed to see it. Deliberately
 * excludes the password hash, lockout state and failure counts - none of which
 * a browser has any use for.
 */
public record CurrentUser(
        Long id,
        String email,
        String fullName,
        String phone,
        List<String> roles,
        boolean mustChangePassword
) {
    public boolean isAdmin() {
        return roles.contains("ROLE_ADMIN");
    }
}

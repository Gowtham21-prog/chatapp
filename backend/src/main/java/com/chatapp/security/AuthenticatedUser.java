package com.chatapp.security;

import java.security.Principal;
import java.util.UUID;

/**
 * Lightweight principal placed in the SecurityContext after JWT validation.
 * Deliberately does NOT hold the full User entity to avoid accidental lazy
 * DB access from filter/interceptor code on every single request.
 */
public record AuthenticatedUser(UUID userId, String username) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }
}

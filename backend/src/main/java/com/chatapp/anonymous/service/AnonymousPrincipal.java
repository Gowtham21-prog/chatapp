package com.chatapp.anonymous.service;

import java.security.Principal;

/**
 * Distinct principal type from AuthenticatedUser (registered users) so
 * downstream code can never accidentally treat an anonymous chat
 * participant as a registered user or vice versa - the compiler enforces
 * the separation.
 */
public record AnonymousPrincipal(String sessionId) implements Principal {

    @Override
    public String getName() {
        return sessionId;
    }
}

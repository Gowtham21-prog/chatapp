package com.chatapp.security;

import com.chatapp.common.exception.UnauthorizedException;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthenticatedUser get() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;

        if (!(principal instanceof AuthenticatedUser authenticatedUser)) {
            throw new UnauthorizedException("Authentication required");
        }
        return authenticatedUser;
    }
}

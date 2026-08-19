package com.chatapp.anonymous.dto;

import java.util.Set;

public record AnonymousSessionResponse(
        String sessionId,
        String accessToken,
        Set<String> interests,
        int expiresInSeconds
) {
}

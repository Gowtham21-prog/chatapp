package com.chatapp.moderation.dto;

import java.time.Instant;
import java.util.UUID;

public record BlockedUserResponse(
        UUID userId,
        String username,
        String displayName,
        Instant blockedAt
) {
}

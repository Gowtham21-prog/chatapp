package com.chatapp.user.dto;

import java.util.UUID;

public record UserSearchResult(
        UUID id,
        String username,
        String displayName,
        String avatarUrl,
        boolean online
) {
}

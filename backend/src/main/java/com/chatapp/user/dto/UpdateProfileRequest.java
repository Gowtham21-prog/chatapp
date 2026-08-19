package com.chatapp.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 1, max = 60) String displayName,
        @Size(max = 300) String bio,
        @Size(max = 500) String avatarUrl
) {
}

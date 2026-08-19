package com.chatapp.auth.dto;

import com.chatapp.user.dto.UserProfileResponse;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        int expiresInSeconds,
        UserProfileResponse user
) {
}

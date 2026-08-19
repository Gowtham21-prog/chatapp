package com.chatapp.notification.dto;

import com.chatapp.notification.entity.NotificationType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        Map<String, Object> metadata,
        boolean read,
        Instant createdAt
) {
}

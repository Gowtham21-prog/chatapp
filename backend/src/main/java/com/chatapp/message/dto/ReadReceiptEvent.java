package com.chatapp.message.dto;

import java.time.Instant;
import java.util.UUID;

public record ReadReceiptEvent(
        UUID conversationId,
        UUID messageId,
        UUID readByUserId,
        Instant readAt
) {
}

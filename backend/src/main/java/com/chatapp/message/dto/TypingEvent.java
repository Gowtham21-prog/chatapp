package com.chatapp.message.dto;

import java.util.UUID;

public record TypingEvent(
        UUID conversationId,
        UUID userId,
        boolean typing
) {
}

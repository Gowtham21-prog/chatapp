package com.chatapp.conversation.dto;

import com.chatapp.message.dto.MessageResponse;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID otherUserId,
        String otherUsername,
        String otherDisplayName,
        String otherAvatarUrl,
        boolean otherOnline,
        MessageResponse lastMessage,
        long unreadCount,
        Instant updatedAt
) {
}

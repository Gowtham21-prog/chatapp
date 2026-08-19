package com.chatapp.message.dto;

import com.chatapp.message.entity.MessageStatus;
import com.chatapp.message.entity.MessageType;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String content,
        MessageType messageType,
        String attachmentUrl,
        String attachmentName,
        Long attachmentSizeBytes,
        String attachmentMimeType,
        MessageStatus status,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt
) {
}

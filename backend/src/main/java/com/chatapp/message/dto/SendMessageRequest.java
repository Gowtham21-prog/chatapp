package com.chatapp.message.dto;

import com.chatapp.message.entity.MessageType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendMessageRequest(
        @NotNull UUID conversationId,

        @Size(max = 5000)
        String content,

        @NotNull MessageType messageType,

        String attachmentUrl,
        String attachmentName,
        Long attachmentSizeBytes,
        String attachmentMimeType
) {
}

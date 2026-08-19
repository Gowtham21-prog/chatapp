package com.chatapp.message.service;

import com.chatapp.message.entity.Message;
import com.chatapp.message.dto.MessageResponse;
import org.springframework.stereotype.Component;

/**
 * Hand-written rather than MapStruct because deleted messages need content
 * masked out entirely (not just a passthrough field mapping) - a soft-
 * deleted message keeps its row for conversation integrity but must never
 * leak its original text/attachment to clients.
 */
@Component
public class MessageMapper {

    public MessageResponse toResponse(Message message) {
        boolean deleted = message.isDeleted();
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                deleted ? null : message.getContent(),
                message.getMessageType(),
                deleted ? null : message.getAttachmentUrl(),
                deleted ? null : message.getAttachmentName(),
                deleted ? null : message.getAttachmentSizeBytes(),
                deleted ? null : message.getAttachmentMimeType(),
                message.getStatus(),
                deleted,
                message.getCreatedAt(),
                message.getUpdatedAt()
        );
    }
}

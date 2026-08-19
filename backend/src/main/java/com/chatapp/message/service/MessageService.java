package com.chatapp.message.service;

import com.chatapp.common.exception.BadRequestException;
import com.chatapp.common.exception.ForbiddenException;
import com.chatapp.common.exception.ResourceNotFoundException;
import com.chatapp.conversation.entity.Conversation;
import com.chatapp.conversation.repository.ConversationParticipantRepository;
import com.chatapp.conversation.repository.ConversationRepository;
import com.chatapp.message.dto.MessageResponse;
import com.chatapp.message.dto.SendMessageRequest;
import com.chatapp.message.entity.Message;
import com.chatapp.message.entity.MessageReceipt;
import com.chatapp.message.entity.MessageStatus;
import com.chatapp.message.entity.MessageType;
import com.chatapp.message.repository.MessageReceiptRepository;
import com.chatapp.message.repository.MessageRepository;
import com.chatapp.moderation.service.ModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageReceiptRepository receiptRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ModerationService moderationService;
    private final MessageMapper messageMapper;

    /**
     * Persists a message and initializes a receipt row (undelivered/unread)
     * for the other participant. Called by the WebSocket layer (primary
     * path for real-time send) - there is no separate REST "send message"
     * endpoint, since sending must broadcast in real time and a REST-only
     * path would silently skip that.
     */
    @Transactional
    public Message send(UUID senderId, SendMessageRequest request) {
        Conversation conversation = conversationRepository.findById(request.conversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (!conversationRepository.isParticipant(conversation.getId(), senderId)) {
            throw new ForbiddenException("You are not a participant in this conversation");
        }

        List<UUID> otherParticipantIds = participantRepository
                .findOtherParticipantIds(conversation.getId(), senderId);

        for (UUID otherId : otherParticipantIds) {
            if (moderationService.isBlockedEitherDirection(senderId, otherId)) {
                throw new ForbiddenException("You cannot message this user");
            }
        }

        if (request.messageType() == MessageType.TEXT
                && (request.content() == null || request.content().isBlank())) {
            throw new BadRequestException("Message content cannot be empty");
        }
        if (request.messageType() != MessageType.TEXT && request.attachmentUrl() == null) {
            throw new BadRequestException("Attachment URL is required for image/file messages");
        }

        Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(senderId)
                .content(request.content())
                .messageType(request.messageType())
                .attachmentUrl(request.attachmentUrl())
                .attachmentName(request.attachmentName())
                .attachmentSizeBytes(request.attachmentSizeBytes())
                .attachmentMimeType(request.attachmentMimeType())
                .status(MessageStatus.SENT)
                .build();
        message = messageRepository.save(message);

        for (UUID otherId : otherParticipantIds) {
            receiptRepository.save(MessageReceipt.builder()
                    .messageId(message.getId())
                    .userId(otherId)
                    .build());
        }

        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        return message;
    }

    @Transactional
    public Message markDelivered(UUID messageId, UUID recipientUserId) {
        Message message = getMessageOrThrow(messageId);
        MessageReceipt receipt = receiptRepository.findByMessageIdAndUserId(messageId, recipientUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found"));

        if (receipt.getDeliveredAt() == null) {
            receipt.setDeliveredAt(Instant.now());
            receiptRepository.save(receipt);
        }
        if (message.getStatus() == MessageStatus.SENT) {
            message.setStatus(MessageStatus.DELIVERED);
            messageRepository.save(message);
        }
        return message;
    }

    @Transactional
    public Message markRead(UUID messageId, UUID recipientUserId) {
        Message message = getMessageOrThrow(messageId);
        MessageReceipt receipt = receiptRepository.findByMessageIdAndUserId(messageId, recipientUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found"));

        Instant now = Instant.now();
        if (receipt.getDeliveredAt() == null) {
            receipt.setDeliveredAt(now);
        }
        receipt.setReadAt(now);
        receiptRepository.save(receipt);

        message.setStatus(MessageStatus.READ);
        return messageRepository.save(message);
    }

    /**
     * Marks every unread message in a conversation as read for one user in
     * a single pass - used when a user opens a conversation thread, so we
     * don't need one WebSocket round-trip per historical unread message.
     */
    @Transactional
    public List<Message> markConversationRead(UUID conversationId, UUID userId) {
        Page<Message> page = messageRepository.findAllByConversationIdOrderByCreatedAtDesc(
                conversationId, Pageable.ofSize(200));

        return page.getContent().stream()
                .filter(m -> !m.getSenderId().equals(userId) && m.getStatus() != MessageStatus.READ)
                .map(m -> markRead(m.getId(), userId))
                .toList();
    }

    @Transactional
    public void deleteMessage(UUID messageId, UUID requesterId, boolean forEveryone) {
        Message message = getMessageOrThrow(messageId);

        if (!message.getSenderId().equals(requesterId)) {
            throw new ForbiddenException("You can only delete your own messages");
        }
        if (forEveryone && message.getCreatedAt().isBefore(Instant.now().minusSeconds(172800))) {
            throw new BadRequestException("Messages older than 48 hours cannot be deleted for everyone");
        }

        message.setDeletedAt(Instant.now());
        message.setDeletedForEveryone(forEveryone);
        messageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getHistory(UUID conversationId, UUID requesterId, Pageable pageable) {
        assertParticipant(conversationId, requesterId);
        return messageRepository.findAllByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                .map(messageMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> searchInConversation(UUID conversationId, UUID requesterId,
                                                        String query, Pageable pageable) {
        assertParticipant(conversationId, requesterId);
        return messageRepository.searchInConversation(conversationId, escapeLikeWildcards(query), pageable)
                .map(messageMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> searchAllConversations(UUID requesterId, String query, Pageable pageable) {
        return messageRepository.searchAcrossUserConversations(requesterId, escapeLikeWildcards(query), pageable)
                .map(messageMapper::toResponse);
    }

    /**
     * The search queries use a SQL LIKE under the hood (via JPQL LIKE +
     * CONCAT), which treats '%' and '_' as wildcards. Without escaping,
     * searching for a literal "50%" or "file_name" would match far more
     * than the user typed. Backslash-escaping here matches the ESCAPE
     * default MySQL/Postgres both honor for LIKE.
     */
    private String escapeLikeWildcards(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private void assertParticipant(UUID conversationId, UUID userId) {
        if (!conversationRepository.isParticipant(conversationId, userId)) {
            throw new ForbiddenException("You are not a participant in this conversation");
        }
    }

    private Message getMessageOrThrow(UUID messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
    }
}

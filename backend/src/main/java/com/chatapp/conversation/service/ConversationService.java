package com.chatapp.conversation.service;

import com.chatapp.common.exception.BadRequestException;
import com.chatapp.common.exception.ForbiddenException;
import com.chatapp.common.exception.ResourceNotFoundException;
import com.chatapp.conversation.dto.ConversationResponse;
import com.chatapp.conversation.entity.Conversation;
import com.chatapp.conversation.entity.ConversationParticipant;
import com.chatapp.conversation.entity.ConversationType;
import com.chatapp.conversation.repository.ConversationParticipantRepository;
import com.chatapp.conversation.repository.ConversationRepository;
import com.chatapp.message.entity.Message;
import com.chatapp.message.entity.MessageStatus;
import com.chatapp.message.repository.MessageRepository;
import com.chatapp.message.service.MessageMapper;
import com.chatapp.moderation.service.ModerationService;
import com.chatapp.presence.PresenceService;
import com.chatapp.user.entity.User;
import com.chatapp.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ModerationService moderationService;
    private final PresenceService presenceService;
    private final MessageMapper messageMapper;

    /**
     * Idempotent: calling this twice for the same pair of users returns the
     * same conversation rather than erroring, so the frontend can always
     * "start or open" a chat without first checking whether one exists.
     */
    @Transactional
    public ConversationResponse startOrGetDirectConversation(UUID currentUserId, UUID otherUserId) {
        if (currentUserId.equals(otherUserId)) {
            throw new BadRequestException("You cannot start a conversation with yourself");
        }
        if (!userRepository.existsById(otherUserId)) {
            throw new ResourceNotFoundException("User not found");
        }
        if (moderationService.isBlockedEitherDirection(currentUserId, otherUserId)) {
            throw new ForbiddenException("You cannot message this user");
        }

        String pairKey = Conversation.directPairKey(currentUserId, otherUserId);
        Conversation conversation = conversationRepository.findByDirectPairKey(pairKey)
                .orElseGet(() -> createDirectConversation(currentUserId, otherUserId, pairKey));

        return toResponse(conversation, currentUserId);
    }

    private Conversation createDirectConversation(UUID userA, UUID userB, String pairKey) {
        Conversation conversation = Conversation.builder()
                .type(ConversationType.DIRECT)
                .directPairKey(pairKey)
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationParticipant p1 = ConversationParticipant.builder()
                .conversation(conversation).userId(userA).build();
        ConversationParticipant p2 = ConversationParticipant.builder()
                .conversation(conversation).userId(userB).build();
        participantRepository.save(p1);
        participantRepository.save(p2);

        return conversation;
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listForUser(UUID userId) {
        return conversationRepository.findAllForUser(userId).stream()
                .map(c -> toResponse(c, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse getById(UUID conversationId, UUID currentUserId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        assertParticipant(conversationId, currentUserId);
        return toResponse(conversation, currentUserId);
    }

    public void assertParticipant(UUID conversationId, UUID userId) {
        if (!conversationRepository.isParticipant(conversationId, userId)) {
            throw new ForbiddenException("You are not a participant in this conversation");
        }
    }

    private ConversationResponse toResponse(Conversation conversation, UUID currentUserId) {
        List<UUID> otherIds = participantRepository.findOtherParticipantIds(conversation.getId(), currentUserId);
        UUID otherUserId = otherIds.isEmpty() ? null : otherIds.get(0);

        User other = otherUserId == null ? null : userRepository.findById(otherUserId).orElse(null);

        Message last = messageRepository
                .findAllByConversationIdOrderByCreatedAtDesc(conversation.getId(), PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);

        long unread = otherUserId == null ? 0 :
                messageRepository.countByConversationIdAndSenderIdNotAndStatusNot(
                        conversation.getId(), currentUserId, MessageStatus.READ);

        return new ConversationResponse(
                conversation.getId(),
                other == null ? null : other.getId(),
                other == null ? null : other.getUsername(),
                other == null ? null : other.getDisplayName(),
                other == null ? null : other.getAvatarUrl(),
                other != null && presenceService.isOnline(other.getId()),
                last == null ? null : messageMapper.toResponse(last),
                unread,
                conversation.getUpdatedAt()
        );
    }
}

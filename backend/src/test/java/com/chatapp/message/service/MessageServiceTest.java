package com.chatapp.message.service;

import com.chatapp.common.exception.BadRequestException;
import com.chatapp.common.exception.ForbiddenException;
import com.chatapp.common.exception.ResourceNotFoundException;
import com.chatapp.conversation.entity.Conversation;
import com.chatapp.conversation.repository.ConversationParticipantRepository;
import com.chatapp.conversation.repository.ConversationRepository;
import com.chatapp.message.dto.SendMessageRequest;
import com.chatapp.message.entity.Message;
import com.chatapp.message.entity.MessageType;
import com.chatapp.message.repository.MessageReceiptRepository;
import com.chatapp.message.repository.MessageRepository;
import com.chatapp.moderation.service.ModerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageReceiptRepository receiptRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationParticipantRepository participantRepository;
    @Mock
    private ModerationService moderationService;
    @Mock
    private MessageMapper messageMapper;

    @InjectMocks
    private MessageService messageService;

    private UUID senderId;
    private UUID recipientId;
    private UUID conversationId;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        senderId = UUID.randomUUID();
        recipientId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        conversation = Conversation.builder().id(conversationId).build();
    }

    @Test
    void send_withValidTextMessage_persistsAndCreatesReceipt() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationRepository.isParticipant(conversationId, senderId)).thenReturn(true);
        when(participantRepository.findOtherParticipantIds(conversationId, senderId))
                .thenReturn(List.of(recipientId));
        when(moderationService.isBlockedEitherDirection(senderId, recipientId)).thenReturn(false);
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        SendMessageRequest request = new SendMessageRequest(
                conversationId, "Hello", MessageType.TEXT, null, null, null, null);

        Message result = messageService.send(senderId, request);

        assertThat(result.getContent()).isEqualTo("Hello");
        assertThat(result.getSenderId()).isEqualTo(senderId);
        verify(receiptRepository).save(argThat(r -> r.getUserId().equals(recipientId)));
    }

    @Test
    void send_whenSenderNotParticipant_throwsForbidden() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationRepository.isParticipant(conversationId, senderId)).thenReturn(false);

        SendMessageRequest request = new SendMessageRequest(
                conversationId, "Hello", MessageType.TEXT, null, null, null, null);

        assertThatThrownBy(() -> messageService.send(senderId, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void send_whenRecipientBlocked_throwsForbidden() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationRepository.isParticipant(conversationId, senderId)).thenReturn(true);
        when(participantRepository.findOtherParticipantIds(conversationId, senderId))
                .thenReturn(List.of(recipientId));
        when(moderationService.isBlockedEitherDirection(senderId, recipientId)).thenReturn(true);

        SendMessageRequest request = new SendMessageRequest(
                conversationId, "Hello", MessageType.TEXT, null, null, null, null);

        assertThatThrownBy(() -> messageService.send(senderId, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void send_withEmptyTextContent_throwsBadRequest() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationRepository.isParticipant(conversationId, senderId)).thenReturn(true);
        when(participantRepository.findOtherParticipantIds(conversationId, senderId))
                .thenReturn(List.of(recipientId));
        when(moderationService.isBlockedEitherDirection(senderId, recipientId)).thenReturn(false);

        SendMessageRequest request = new SendMessageRequest(
                conversationId, "   ", MessageType.TEXT, null, null, null, null);

        assertThatThrownBy(() -> messageService.send(senderId, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void send_withImageTypeButNoAttachmentUrl_throwsBadRequest() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationRepository.isParticipant(conversationId, senderId)).thenReturn(true);
        when(participantRepository.findOtherParticipantIds(conversationId, senderId))
                .thenReturn(List.of(recipientId));
        when(moderationService.isBlockedEitherDirection(senderId, recipientId)).thenReturn(false);

        SendMessageRequest request = new SendMessageRequest(
                conversationId, null, MessageType.IMAGE, null, null, null, null);

        assertThatThrownBy(() -> messageService.send(senderId, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void send_whenConversationDoesNotExist_throwsNotFound() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.empty());

        SendMessageRequest request = new SendMessageRequest(
                conversationId, "Hi", MessageType.TEXT, null, null, null, null);

        assertThatThrownBy(() -> messageService.send(senderId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteMessage_byNonSender_throwsForbidden() {
        Message message = Message.builder()
                .id(UUID.randomUUID())
                .senderId(senderId)
                .conversationId(conversationId)
                .content("hi")
                .createdAt(java.time.Instant.now())
                .build();
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> messageService.deleteMessage(message.getId(), recipientId, false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteMessage_forEveryoneAfter48Hours_throwsBadRequest() {
        Message message = Message.builder()
                .id(UUID.randomUUID())
                .senderId(senderId)
                .conversationId(conversationId)
                .content("hi")
                .createdAt(java.time.Instant.now().minusSeconds(200_000))
                .build();
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> messageService.deleteMessage(message.getId(), senderId, true))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteMessage_byOwnerWithinWindow_softDeletesSuccessfully() {
        Message message = Message.builder()
                .id(UUID.randomUUID())
                .senderId(senderId)
                .conversationId(conversationId)
                .content("hi")
                .createdAt(java.time.Instant.now())
                .build();
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        messageService.deleteMessage(message.getId(), senderId, true);

        assertThat(message.isDeleted()).isTrue();
        assertThat(message.isDeletedForEveryone()).isTrue();
    }
}

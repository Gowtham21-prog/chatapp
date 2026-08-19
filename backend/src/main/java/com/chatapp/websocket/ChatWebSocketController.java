package com.chatapp.websocket;

import com.chatapp.common.exception.RateLimitExceededException;
import com.chatapp.common.util.RateLimiter;
import com.chatapp.config.RateLimitProperties;
import com.chatapp.message.dto.MessageResponse;
import com.chatapp.message.dto.ReadReceiptEvent;
import com.chatapp.message.dto.SendMessageRequest;
import com.chatapp.message.dto.TypingEvent;
import com.chatapp.message.entity.Message;
import com.chatapp.message.service.MessageMapper;
import com.chatapp.message.service.MessageService;
import com.chatapp.notification.service.NotificationService;
import com.chatapp.presence.PresenceService;
import com.chatapp.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * All application-level real-time actions live under /app/** per
 * WebSocketConfig's applicationDestinationPrefixes. Delivery to the
 * *other* participant(s) of a conversation uses convertAndSendToUser,
 * which routes to a per-user destination regardless of how many devices/
 * tabs that user has connected - each of their active sessions receives
 * the frame independently.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final MessageService messageService;
    private final MessageMapper messageMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final com.chatapp.conversation.repository.ConversationParticipantRepository participantRepository;
    private final NotificationService notificationService;
    private final com.chatapp.user.repository.UserRepository userRepository;

    @MessageMapping("/chat.send")
    public void sendMessage(@Valid @Payload SendMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
        AuthenticatedUser sender = requireUser(headerAccessor);

        boolean allowed = rateLimiter.tryConsume("ratelimit:ws:send:" + sender.userId(),
                rateLimitProperties.messagesPerMinute(), Duration.ofMinutes(1));
        if (!allowed) {
            throw new RateLimitExceededException("You're sending messages too quickly");
        }

        Message saved = messageService.send(sender.userId(), request);
        MessageResponse response = messageMapper.toResponse(saved);

        List<UUID> recipients = participantRepository.findOtherParticipantIds(saved.getConversationId(), sender.userId());

        messagingTemplate.convertAndSendToUser(sender.userId().toString(), "/queue/messages", response);
        for (UUID recipientId : recipients) {
            messagingTemplate.convertAndSendToUser(recipientId.toString(), "/queue/messages", response);

            if (!presenceService.isOnline(recipientId)) {
                String senderName = userRepository.findById(sender.userId())
                        .map(com.chatapp.user.entity.User::getDisplayName)
                        .orElse(sender.username());
                String preview = response.content() != null && response.content().length() > 80
                        ? response.content().substring(0, 80) + "…"
                        : response.content();
                notificationService.notifyNewMessage(recipientId, saved.getConversationId(), senderName, preview);
            }
        }
    }

    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingEvent event, SimpMessageHeaderAccessor headerAccessor) {
        AuthenticatedUser sender = requireUser(headerAccessor);

        List<UUID> recipients = participantRepository.findOtherParticipantIds(event.conversationId(), sender.userId());
        TypingEvent outbound = new TypingEvent(event.conversationId(), sender.userId(), event.typing());

        for (UUID recipientId : recipients) {
            messagingTemplate.convertAndSendToUser(recipientId.toString(), "/queue/typing", outbound);
        }
    }

    @MessageMapping("/chat.delivered")
    public void delivered(@Payload UUID messageId, SimpMessageHeaderAccessor headerAccessor) {
        AuthenticatedUser recipient = requireUser(headerAccessor);
        Message updated = messageService.markDelivered(messageId, recipient.userId());
        notifySender(updated, recipient.userId(), false);
    }

    @MessageMapping("/chat.read")
    public void read(@Payload UUID messageId, SimpMessageHeaderAccessor headerAccessor) {
        AuthenticatedUser recipient = requireUser(headerAccessor);
        Message updated = messageService.markRead(messageId, recipient.userId());
        notifySender(updated, recipient.userId(), true);
    }

    @MessageMapping("/presence.heartbeat")
    public void heartbeat(SimpMessageHeaderAccessor headerAccessor) {
        AuthenticatedUser user = requireUser(headerAccessor);
        presenceService.heartbeat(user.userId(), headerAccessor.getSessionId());
    }

    private void notifySender(Message message, UUID readByUserId, boolean read) {
        ReadReceiptEvent event = new ReadReceiptEvent(
                message.getConversationId(), message.getId(), readByUserId, java.time.Instant.now());
        messagingTemplate.convertAndSendToUser(
                message.getSenderId().toString(), read ? "/queue/read-receipts" : "/queue/delivery-receipts", event);
    }

    private AuthenticatedUser requireUser(SimpMessageHeaderAccessor headerAccessor) {
        var principal = headerAccessor.getUser();
        if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new IllegalStateException("Unauthenticated WebSocket message");
    }
}

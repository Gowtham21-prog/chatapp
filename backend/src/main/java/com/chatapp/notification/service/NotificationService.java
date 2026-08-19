package com.chatapp.notification.service;

import com.chatapp.notification.dto.NotificationResponse;
import com.chatapp.notification.entity.Notification;
import com.chatapp.notification.entity.NotificationType;
import com.chatapp.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Notifications are persisted (so a user sees what they missed while
 * offline / in the notifications tab) AND pushed live over the same
 * WebSocket connection used for chat (so an online user sees it appear
 * immediately without polling). The two concerns - durability and
 * real-time delivery - are handled together here rather than splitting
 * "notification storage" from "notification push" across services, since
 * every notification in this app needs both.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void notifyNewMessage(UUID recipientUserId, UUID conversationId, String senderDisplayName, String preview) {
        Notification notification = Notification.builder()
                .userId(recipientUserId)
                .type(NotificationType.NEW_MESSAGE)
                .title(senderDisplayName)
                .body(preview)
                .metadata(Map.of("conversationId", conversationId.toString()))
                .build();
        notification = notificationRepository.save(notification);

        messagingTemplate.convertAndSendToUser(
                recipientUserId.toString(), "/queue/notifications", toResponse(notification));
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(UUID userId, Pageable pageable) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllRead(userId, Instant.now());
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getMetadata(),
                n.getReadAt() != null, n.getCreatedAt());
    }
}

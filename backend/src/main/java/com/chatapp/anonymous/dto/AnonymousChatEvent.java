package com.chatapp.anonymous.dto;

public record AnonymousChatEvent(
        String type, // MESSAGE, TYPING, PARTNER_LEFT, PARTNER_DISCONNECTED
        String roomId,
        String senderSessionId,
        String content,
        java.time.Instant timestamp
) {
}

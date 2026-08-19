package com.chatapp.anonymous.controller;

import com.chatapp.anonymous.dto.AnonymousChatEvent;
import com.chatapp.anonymous.service.AnonymousMatchmakingService;
import com.chatapp.anonymous.service.AnonymousPrincipal;
import com.chatapp.common.exception.ForbiddenException;
import com.chatapp.common.exception.RateLimitExceededException;
import com.chatapp.common.util.RateLimiter;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Anonymous chat messages are NEVER persisted to Postgres - they are
 * relayed directly, socket to socket, via the room's two session ids. This
 * is the concrete expression of "temporary anonymous sessions ... not
 * permanently tied to an identity": there is no message row anywhere for
 * anonymous conversations, so there is nothing to retain beyond normal
 * WebSocket transport logs.
 */
@Controller
@RequiredArgsConstructor
public class AnonymousWebSocketController {

    private final AnonymousMatchmakingService matchmakingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RateLimiter rateLimiter;

    @MessageMapping("/anon.send")
    public void send(@Payload AnonymousSendRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSession(headerAccessor);

        boolean allowed = rateLimiter.tryConsume(
                "ratelimit:anon:send:" + sessionId, 60, Duration.ofMinutes(1));
        if (!allowed) {
            throw new RateLimitExceededException("You're sending messages too quickly");
        }

        String roomId = matchmakingService.currentRoomOf(sessionId)
                .orElseThrow(() -> new ForbiddenException("You are not in an active chat"));
        String partnerId = matchmakingService.partnerSessionId(sessionId, roomId)
                .orElseThrow(() -> new ForbiddenException("Your chat partner has left"));

        AnonymousChatEvent event = new AnonymousChatEvent(
                "MESSAGE", roomId, sessionId, request.content(), Instant.now());

        messagingTemplate.convertAndSendToUser(partnerId, "/queue/anon-events", event);
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/anon-events", event); // echo to sender
    }

    @MessageMapping("/anon.typing")
    public void typing(@Payload AnonymousTypingRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSession(headerAccessor);
        Optional<String> roomId = matchmakingService.currentRoomOf(sessionId);
        if (roomId.isEmpty()) {
            return;
        }
        matchmakingService.partnerSessionId(sessionId, roomId.get()).ifPresent(partnerId -> {
            AnonymousChatEvent event = new AnonymousChatEvent(
                    "TYPING", roomId.get(), sessionId, String.valueOf(request.typing()), Instant.now());
            messagingTemplate.convertAndSendToUser(partnerId, "/queue/anon-events", event);
        });
    }

    private String requireSession(SimpMessageHeaderAccessor headerAccessor) {
        var principal = headerAccessor.getUser();
        if (principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AnonymousPrincipal anon) {
            return anon.sessionId();
        }
        throw new IllegalStateException("Unauthenticated anonymous WebSocket message");
    }

    public record AnonymousSendRequest(@NotBlank String content) {
    }

    public record AnonymousTypingRequest(boolean typing) {
    }
}

package com.chatapp.websocket;

import com.chatapp.anonymous.dto.AnonymousChatEvent;
import com.chatapp.anonymous.service.AnonymousMatchmakingService;
import com.chatapp.anonymous.service.AnonymousPrincipal;
import com.chatapp.presence.PresenceService;
import com.chatapp.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;

/**
 * A user is "online" the moment their first STOMP session connects, and
 * "offline" only once their LAST session disconnects (PresenceService
 * tracks a set of session ids per user, see its Javadoc). We broadcast a
 * presence event to a public topic other users can subscribe to for a
 * specific counterpart rather than pushing to every connected client, to
 * avoid an O(n) fan-out of presence noise as the user base grows.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceEventListener {

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AnonymousMatchmakingService anonymousMatchmakingService;

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        AuthenticatedUser user = extractUser(accessor.getUser());
        if (user == null) {
            return;
        }
        presenceService.markOnline(user.userId(), accessor.getSessionId());
        broadcastPresence(user.userId(), true);
        log.debug("User {} connected, session {}", user.username(), accessor.getSessionId());
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        AuthenticatedUser user = extractUser(accessor.getUser());
        if (user != null) {
            boolean fullyOffline = presenceService.markSessionClosed(user.userId(), accessor.getSessionId());
            if (fullyOffline) {
                broadcastPresence(user.userId(), false);
            }
            log.debug("User {} disconnected, session {}", user.username(), accessor.getSessionId());
            return;
        }

        AnonymousPrincipal anon = extractAnonymous(accessor.getUser());
        if (anon != null) {
            handleAnonymousDisconnect(anon.sessionId());
        }
    }

    /**
     * A dropped WebSocket connection (tab closed, network loss) is treated
     * the same as an explicit "leave": the partner is told immediately
     * rather than being left waiting on a room that's already dead, and the
     * room mapping is torn down so a stale roomId can't be reused.
     */
    private void handleAnonymousDisconnect(String sessionId) {
        anonymousMatchmakingService.currentRoomOf(sessionId).ifPresent(roomId -> {
            anonymousMatchmakingService.partnerSessionId(sessionId, roomId).ifPresent(partnerId -> {
                messagingTemplate.convertAndSendToUser(partnerId, "/queue/anon-events",
                        new AnonymousChatEvent("PARTNER_DISCONNECTED", roomId, sessionId, null, Instant.now()));
            });
        });
        anonymousMatchmakingService.leaveRoom(sessionId);
        anonymousMatchmakingService.leaveQueue(sessionId);
    }

    private void broadcastPresence(java.util.UUID userId, boolean online) {
        messagingTemplate.convertAndSend("/topic/presence/" + userId,
                new PresenceUpdateMessage(userId, online));
    }

    private AuthenticatedUser extractUser(Principal principal) {
        if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }

    private AnonymousPrincipal extractAnonymous(Principal principal) {
        if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AnonymousPrincipal anon) {
            return anon;
        }
        return null;
    }

    public record PresenceUpdateMessage(java.util.UUID userId, boolean online) {
    }
}

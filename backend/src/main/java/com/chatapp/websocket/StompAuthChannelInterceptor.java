package com.chatapp.websocket;

import com.chatapp.anonymous.service.AnonymousMatchmakingService;
import com.chatapp.anonymous.service.AnonymousPrincipal;
import com.chatapp.anonymous.service.AnonymousTokenService;
import com.chatapp.security.AuthenticatedUser;
import com.chatapp.security.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Authenticates the STOMP CONNECT frame using a JWT access token passed as
 * a header (Authorization: Bearer <token>) or a "token" STOMP header (for
 * browser SockJS clients where custom Authorization headers on the initial
 * HTTP upgrade aren't always convenient to set). Once authenticated, the
 * resulting Principal is attached to the STOMP session and is available for
 * the lifetime of that WebSocket connection - individual SEND frames do not
 * need to re-authenticate.
 *
 * IMPORTANT: the accessor MUST be obtained via
 * MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class) here,
 * not StompHeaderAccessor.wrap(message). wrap() can return a detached copy
 * whose setUser() mutation never propagates back into the message that
 * continues down the channel - the CONNECT appears to succeed and the
 * principal looks correct right here, but every later frame on the same
 * STOMP session sees headerAccessor.getUser() == null. This is a documented
 * Spring issue (spring-projects/spring-framework#20078); getAccessor()
 * returns the actual mutable accessor already attached to the message.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final AnonymousTokenService anonymousTokenService;
    private final AnonymousMatchmakingService anonymousMatchmakingService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            Object principal = resolvePrincipal(accessor);

            if (principal == null) {
                log.debug("Rejected WebSocket CONNECT: missing or invalid token");
                throw new org.springframework.messaging.simp.stomp.StompConversionException(
                        "Authentication required");
            }

            accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        }

        return message;
    }

    /**
     * Two independent auth schemes share this one WebSocket endpoint:
     * registered users authenticate with their normal access JWT (mode:
     * absent or "user"), anonymous chat participants authenticate with
     * their anonymous session token (mode: "anonymous"). The client
     * declares which one it's using via the "mode" STOMP header so we don't
     * have to guess by trying both token validators against every token.
     */
    private Object resolvePrincipal(StompHeaderAccessor accessor) {
        String mode = accessor.getFirstNativeHeader("mode");
        Optional<String> token = extractToken(accessor);

        if ("anonymous".equals(mode)) {
            return token.flatMap(anonymousTokenService::validateAndExtractSessionId)
                    .filter(anonymousMatchmakingService::isSessionAlive)
                    .map(AnonymousPrincipal::new)
                    .orElse(null);
        }

        return token.flatMap(jwtService::parseAndValidate)
                .map(this::toPrincipal)
                .orElse(null);
    }

    private AuthenticatedUser toPrincipal(Claims claims) {
        return new AuthenticatedUser(jwtService.extractUserId(claims), jwtService.extractUsername(claims));
    }

    private Optional<String> extractToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return Optional.of(authHeader.substring(7));
        }
        String tokenHeader = accessor.getFirstNativeHeader("token");
        if (tokenHeader != null && !tokenHeader.isBlank()) {
            return Optional.of(tokenHeader);
        }
        return Optional.empty();
    }
}

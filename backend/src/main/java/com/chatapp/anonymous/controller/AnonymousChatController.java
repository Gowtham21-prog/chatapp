package com.chatapp.anonymous.controller;

import com.chatapp.anonymous.dto.AnonymousSessionResponse;
import com.chatapp.anonymous.dto.CreateAnonymousSessionRequest;
import com.chatapp.anonymous.dto.MatchResultResponse;
import com.chatapp.anonymous.service.AnonymousChatService;
import com.chatapp.anonymous.service.AnonymousMatchmakingService;
import com.chatapp.anonymous.service.AnonymousTokenService;
import com.chatapp.common.exception.RateLimitExceededException;
import com.chatapp.common.exception.UnauthorizedException;
import com.chatapp.common.util.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Optional;

/**
 * Public (unauthenticated by JWT) but self-contained: every operation past
 * session creation requires the anonymous session token issued here, which
 * is validated per-request against the "anon:alive:{sessionId}" Redis key -
 * effectively its own lightweight auth scheme scoped to anonymous mode.
 */
@RestController
@RequestMapping("/api/anonymous")
@RequiredArgsConstructor
public class AnonymousChatController {

    private final AnonymousChatService anonymousChatService;
    private final AnonymousMatchmakingService matchmakingService;
    private final AnonymousTokenService tokenService;
    private final RateLimiter rateLimiter;

    @PostMapping("/session")
    public ResponseEntity<AnonymousSessionResponse> createSession(
            @Valid @RequestBody(required = false) CreateAnonymousSessionRequest request,
            HttpServletRequest httpRequest) {

        boolean allowed = rateLimiter.tryConsume(
                "ratelimit:anon:session:" + clientIp(httpRequest), 20, Duration.ofMinutes(1));
        if (!allowed) {
            throw new RateLimitExceededException("Too many session attempts, please slow down");
        }

        var interests = request == null ? null : request.interests();
        return ResponseEntity.ok(anonymousChatService.createSession(interests));
    }

    @PostMapping("/match")
    public ResponseEntity<MatchResultResponse> requestMatch(@RequestHeader("X-Anonymous-Token") String token) {
        String sessionId = requireValidSession(token);

        Optional<AnonymousMatchmakingService.MatchResult> existing = matchmakingService.checkExistingMatch(sessionId);
        if (existing.isPresent()) {
            return ResponseEntity.ok(toResponse(existing.get()));
        }

        Optional<AnonymousMatchmakingService.MatchResult> result = matchmakingService.tryMatch(sessionId);
        return ResponseEntity.ok(result.map(this::toResponse)
                .orElse(new MatchResultResponse("WAITING", null, null, null)));
    }

    @GetMapping("/match")
    public ResponseEntity<MatchResultResponse> pollMatch(@RequestHeader("X-Anonymous-Token") String token) {
        String sessionId = requireValidSession(token);
        Optional<AnonymousMatchmakingService.MatchResult> result = matchmakingService.checkExistingMatch(sessionId);
        return ResponseEntity.ok(result.map(this::toResponse)
                .orElse(new MatchResultResponse("WAITING", null, null, null)));
    }

    @PostMapping("/next")
    public ResponseEntity<MatchResultResponse> next(@RequestHeader("X-Anonymous-Token") String token) {
        String sessionId = requireValidSession(token);
        matchmakingService.leaveRoom(sessionId);
        matchmakingService.leaveQueue(sessionId);

        Optional<AnonymousMatchmakingService.MatchResult> result = matchmakingService.tryMatch(sessionId);
        return ResponseEntity.ok(result.map(this::toResponse)
                .orElse(new MatchResultResponse("WAITING", null, null, null)));
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leave(@RequestHeader("X-Anonymous-Token") String token) {
        String sessionId = requireValidSession(token);
        matchmakingService.leaveRoom(sessionId);
        matchmakingService.leaveQueue(sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Blocks the current partner (so they'll never be re-matched with this
     * session again) and immediately leaves the room - the anonymous-mode
     * equivalent of registered-user blocking, scoped to Redis rather than
     * Postgres since anonymous identity is never persisted.
     */
    @PostMapping("/block")
    public ResponseEntity<Void> blockCurrentPartner(@RequestHeader("X-Anonymous-Token") String token) {
        String sessionId = requireValidSession(token);
        String roomId = matchmakingService.currentRoomOf(sessionId).orElse(null);
        if (roomId != null) {
            matchmakingService.partnerSessionId(sessionId, roomId)
                    .ifPresent(partnerId -> matchmakingService.blockPartnerAndLeave(sessionId, partnerId));
        }
        return ResponseEntity.noContent().build();
    }

    private MatchResultResponse toResponse(AnonymousMatchmakingService.MatchResult result) {
        return new MatchResultResponse("MATCHED", result.roomId(), result.partnerSessionId(), result.sharedInterests());
    }

    private String requireValidSession(String token) {
        return tokenService.validateAndExtractSessionId(token)
                .filter(matchmakingService::isSessionAlive)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired anonymous session"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

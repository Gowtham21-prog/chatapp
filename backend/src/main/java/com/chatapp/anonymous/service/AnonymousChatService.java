package com.chatapp.anonymous.service;

import com.chatapp.anonymous.dto.AnonymousSessionResponse;
import com.chatapp.config.AnonymousProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Anonymous identity is intentionally minimal: a random session id and a
 * signed token, with an optional interest-tag set. Nothing here is ever
 * linked to a User row - that separation is the whole point of "anonymous"
 * mode. Reports/blocks against an anonymous session reference the session
 * id as a bare string (see moderation schema) precisely so this service
 * never needs to know about the users table.
 */
@Service
@RequiredArgsConstructor
public class AnonymousChatService {

    private final AnonymousMatchmakingService matchmakingService;
    private final AnonymousTokenService tokenService;
    private final AnonymousProperties anonymousProperties;

    public AnonymousSessionResponse createSession(Set<String> interests) {
        String sessionId = "anon_" + UUID.randomUUID();
        Set<String> normalizedInterests = normalize(interests);

        matchmakingService.registerSession(sessionId, normalizedInterests);
        String token = tokenService.generateToken(sessionId, anonymousProperties.sessionTtlHours());

        return new AnonymousSessionResponse(
                sessionId, token, normalizedInterests, anonymousProperties.sessionTtlHours() * 3600);
    }

    private Set<String> normalize(Set<String> interests) {
        if (interests == null) {
            return Set.of();
        }
        return interests.stream()
                .filter(i -> i != null && !i.isBlank())
                .map(i -> i.trim().toLowerCase())
                .limit(10)
                .collect(Collectors.toUnmodifiableSet());
    }
}

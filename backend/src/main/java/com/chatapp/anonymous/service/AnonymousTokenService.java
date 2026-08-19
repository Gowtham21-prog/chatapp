package com.chatapp.anonymous.service;

import com.chatapp.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/**
 * Anonymous sessions get their OWN token type (distinct claim, never shares
 * a code path with JwtService's registered-user tokens) so an anonymous
 * token can never be mistaken for / escalated into a registered user's
 * access token, and anonymous auth can have a different (shorter,
 * session-bound) lifetime policy without touching registered-user JWT
 * config.
 */
@Service
@RequiredArgsConstructor
public class AnonymousTokenService {

    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String TOKEN_TYPE_ANONYMOUS = "anonymous";

    private final JwtProperties jwtProperties;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes());
    }

    public String generateToken(String sessionId, int ttlHours) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(sessionId)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ANONYMOUS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlHours, ChronoUnit.HOURS)))
                .signWith(signingKey())
                .compact();
    }

    public Optional<String> validateAndExtractSessionId(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey()).build()
                    .parseSignedClaims(token).getPayload();
            if (!TOKEN_TYPE_ANONYMOUS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

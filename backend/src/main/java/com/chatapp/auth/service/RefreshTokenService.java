package com.chatapp.auth.service;

import com.chatapp.common.exception.UnauthorizedException;
import com.chatapp.config.JwtProperties;
import com.chatapp.user.entity.RefreshToken;
import com.chatapp.user.entity.User;
import com.chatapp.user.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * Refresh tokens are opaque random strings (NOT JWTs). Only their SHA-256
 * hash is persisted, so a leaked database dump doesn't hand out usable
 * tokens. Each refresh call rotates the token (old one revoked, new one
 * issued) - this lets us detect reuse of a revoked token as a signal of
 * theft in a future iteration.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public String issue(User user) {
        String rawToken = generateRawToken();
        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(Instant.now().plus(jwtProperties.refreshTokenTtlDays(), ChronoUnit.DAYS))
                .build();
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    @Transactional
    public User consumeAndRotate(String rawToken, RefreshRotationResult result) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (!existing.isActive()) {
            throw new UnauthorizedException("Refresh token expired or already used");
        }

        existing.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existing);

        User user = existing.getUser();
        result.setNewRawToken(issue(user));
        return user;
    }

    @Transactional
    public void revokeAllForUser(java.util.UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes());
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Small mutable out-param so consumeAndRotate can return both the user
     * and the newly issued raw token without a throwaway record wrapper.
     */
    public static class RefreshRotationResult {
        private String newRawToken;

        public String getNewRawToken() {
            return newRawToken;
        }

        void setNewRawToken(String newRawToken) {
            this.newRawToken = newRawToken;
        }
    }
}

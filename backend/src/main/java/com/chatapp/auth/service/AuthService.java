package com.chatapp.auth.service;

import com.chatapp.auth.dto.*;
import com.chatapp.common.exception.ConflictException;
import com.chatapp.common.exception.UnauthorizedException;
import com.chatapp.security.JwtService;
import com.chatapp.user.dto.UserMapper;
import com.chatapp.user.entity.User;
import com.chatapp.user.entity.UserStatus;
import com.chatapp.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ConflictException("Username is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .status(UserStatus.ACTIVE)
                .build();
        user = userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsernameIgnoreCase(request.usernameOrEmail())
                .or(() -> userRepository.findByEmailIgnoreCase(request.usernameOrEmail()))
                .orElseThrow(() -> new UnauthorizedException("Invalid username/email or password"));

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new UnauthorizedException("This account has been suspended");
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new UnauthorizedException("Invalid username/email or password");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username/email or password");
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshTokenService.RefreshRotationResult result = new RefreshTokenService.RefreshRotationResult();
        User user = refreshTokenService.consumeAndRotate(request.refreshToken(), result);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername());
        return new AuthResponse(accessToken, result.getNewRawToken(),
                jwtService.accessTokenTtlSeconds(), userMapper.toProfileResponse(user));
    }

    @Transactional
    public void logout(java.util.UUID userId) {
        refreshTokenService.revokeAllForUser(userId);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = refreshTokenService.issue(user);
        return new AuthResponse(accessToken, refreshToken,
                jwtService.accessTokenTtlSeconds(), userMapper.toProfileResponse(user));
    }
}

package com.chatapp.user.service;

import com.chatapp.common.exception.ResourceNotFoundException;
import com.chatapp.moderation.service.ModerationService;
import com.chatapp.presence.PresenceService;
import com.chatapp.user.dto.*;
import com.chatapp.user.entity.User;
import com.chatapp.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PresenceService presenceService;
    private final ModerationService moderationService;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return userMapper.toProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.bio() != null) {
            user.setBio(request.bio());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl());
        }
        return userMapper.toProfileResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserSearchResult> search(UUID currentUserId, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return userRepository.searchActiveUsers(query.trim(), currentUserId).stream()
                // Blocked-either-direction users are excluded from search results
                // so a blocked user can't even find you to start a new conversation.
                .filter(u -> !moderationService.isBlockedEitherDirection(currentUserId, u.getId()))
                .map(u -> new UserSearchResult(u.getId(), u.getUsername(), u.getDisplayName(),
                        u.getAvatarUrl(), presenceService.isOnline(u.getId())))
                .toList();
    }
}

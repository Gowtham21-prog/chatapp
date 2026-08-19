package com.chatapp.moderation.service;

import com.chatapp.common.exception.BadRequestException;
import com.chatapp.common.exception.ConflictException;
import com.chatapp.common.exception.ResourceNotFoundException;
import com.chatapp.moderation.dto.BlockedUserResponse;
import com.chatapp.moderation.dto.CreateReportRequest;
import com.chatapp.moderation.entity.Report;
import com.chatapp.moderation.entity.ReportContext;
import com.chatapp.moderation.entity.UserBlock;
import com.chatapp.moderation.repository.ReportRepository;
import com.chatapp.moderation.repository.UserBlockRepository;
import com.chatapp.user.entity.User;
import com.chatapp.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModerationService {

    private final UserBlockRepository userBlockRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    @Transactional
    public void blockUser(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new BadRequestException("You cannot block yourself");
        }
        if (!userRepository.existsById(blockedId)) {
            throw new ResourceNotFoundException("User not found");
        }
        if (userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            throw new ConflictException("User is already blocked");
        }
        userBlockRepository.save(UserBlock.builder()
                .blockerId(blockerId)
                .blockedId(blockedId)
                .build());
    }

    @Transactional
    public void unblockUser(UUID blockerId, UUID blockedId) {
        userBlockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .ifPresent(userBlockRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<BlockedUserResponse> listBlockedUsers(UUID blockerId) {
        return userBlockRepository.findAllByBlockerId(blockerId).stream()
                .map(block -> {
                    User user = userRepository.findById(block.getBlockedId()).orElse(null);
                    if (user == null) {
                        return null;
                    }
                    return new BlockedUserResponse(user.getId(), user.getUsername(),
                            user.getDisplayName(), block.getCreatedAt());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public boolean isBlockedEitherDirection(UUID userA, UUID userB) {
        return userBlockRepository.existsEitherDirection(userA, userB);
    }

    @Transactional
    public void createReport(UUID reporterUserId, String reporterAnonymousId, CreateReportRequest request) {
        if (request.context() == ReportContext.DIRECT && request.reportedUserId() == null) {
            throw new BadRequestException("reportedUserId is required for a direct-chat report");
        }
        if (request.context() == ReportContext.ANONYMOUS && request.reportedAnonymousId() == null) {
            throw new BadRequestException("reportedAnonymousId is required for an anonymous-chat report");
        }

        Report report = Report.builder()
                .reporterUserId(reporterUserId)
                .reporterAnonymousId(reporterAnonymousId)
                .reportedUserId(request.reportedUserId())
                .reportedAnonymousId(request.reportedAnonymousId())
                .context(request.context())
                .reason(request.reason())
                .details(request.details())
                .messageId(request.messageId())
                .build();

        reportRepository.save(report);
    }
}

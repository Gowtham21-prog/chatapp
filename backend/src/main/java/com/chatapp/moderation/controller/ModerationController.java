package com.chatapp.moderation.controller;

import com.chatapp.anonymous.service.AnonymousMatchmakingService;
import com.chatapp.anonymous.service.AnonymousTokenService;
import com.chatapp.common.exception.UnauthorizedException;
import com.chatapp.moderation.dto.BlockUserRequest;
import com.chatapp.moderation.dto.BlockedUserResponse;
import com.chatapp.moderation.dto.CreateReportRequest;
import com.chatapp.moderation.service.ModerationService;
import com.chatapp.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderationService;
    private final AnonymousTokenService anonymousTokenService;
    private final AnonymousMatchmakingService anonymousMatchmakingService;

    @PostMapping("/blocks")
    public ResponseEntity<Void> blockUser(@Valid @RequestBody BlockUserRequest request) {
        moderationService.blockUser(CurrentUser.get().userId(), request.userId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/blocks/{userId}")
    public ResponseEntity<Void> unblockUser(@PathVariable UUID userId) {
        moderationService.unblockUser(CurrentUser.get().userId(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/blocks")
    public ResponseEntity<List<BlockedUserResponse>> listBlocked() {
        return ResponseEntity.ok(moderationService.listBlockedUsers(CurrentUser.get().userId()));
    }

    @PostMapping("/reports")
    public ResponseEntity<Void> report(@Valid @RequestBody CreateReportRequest request) {
        moderationService.createReport(CurrentUser.get().userId(), null, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Separate, unauthenticated-by-JWT endpoint so an anonymous chat
     * participant can report their partner without ever creating an
     * account. Identity is instead proven by the anonymous session token,
     * matching how the rest of anonymous mode authenticates.
     */
    @PostMapping("/reports/anonymous")
    public ResponseEntity<Void> reportAnonymous(
            @RequestHeader("X-Anonymous-Token") String token,
            @Valid @RequestBody CreateReportRequest request) {

        String reporterSessionId = anonymousTokenService.validateAndExtractSessionId(token)
                .filter(anonymousMatchmakingService::isSessionAlive)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired anonymous session"));

        moderationService.createReport(null, reporterSessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}

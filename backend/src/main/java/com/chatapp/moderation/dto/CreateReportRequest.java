package com.chatapp.moderation.dto;

import com.chatapp.moderation.entity.ReportContext;
import com.chatapp.moderation.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateReportRequest(
        @NotNull ReportContext context,

        // Exactly one of these two must be present depending on context;
        // enforced in the service layer rather than bean validation since
        // the rule is conditional on `context`.
        UUID reportedUserId,
        String reportedAnonymousId,

        @NotNull ReportReason reason,
        @Size(max = 1000) String details,
        UUID messageId
) {
}

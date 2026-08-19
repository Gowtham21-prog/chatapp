package com.chatapp.anonymous.dto;

public record MatchResultResponse(
        String status, // WAITING, MATCHED
        String roomId,
        String partnerSessionId,
        java.util.Set<String> sharedInterests
) {
}

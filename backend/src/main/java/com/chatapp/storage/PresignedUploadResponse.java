package com.chatapp.storage;

public record PresignedUploadResponse(
        String uploadUrl,
        String downloadUrl,
        String objectKey,
        int expiresInSeconds
) {
}

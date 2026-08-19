package com.chatapp.storage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PresignedUploadRequest(
        @NotBlank String fileName,
        @NotBlank String contentType,
        @NotNull @Max(15_000_000) long sizeBytes
) {
}

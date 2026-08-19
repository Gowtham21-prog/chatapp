package com.chatapp.storage;

import com.chatapp.common.exception.BadRequestException;
import com.chatapp.config.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Files never pass through the Spring Boot process. The backend only issues
 * a short-lived presigned PUT URL; the browser uploads directly to MinIO/S3,
 * and later downloads directly from S3's public URL too. This keeps large
 * file transfer off the application server entirely, which is the standard
 * pattern for file sharing at any real scale.
 */
@Service
@RequiredArgsConstructor
public class StorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp",
            "application/pdf", "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip"
    );

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public PresignedUploadResponse createPresignedUpload(UUID uploaderId, PresignedUploadRequest request) {
        if (!ALLOWED_CONTENT_TYPES.contains(request.contentType())) {
            throw new BadRequestException("File type not allowed: " + request.contentType());
        }

        String safeFileName = sanitizeFileName(request.fileName());
        String objectKey = "uploads/%s/%s-%s".formatted(uploaderId, UUID.randomUUID(), safeFileName);

        int expirySeconds = 300;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(objectKey)
                .contentType(request.contentType())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        String downloadUrl = s3Properties.publicUrl() + "/" + objectKey;

        return new PresignedUploadResponse(
                presigned.url().toString(), downloadUrl, objectKey, expirySeconds);
    }

    private String sanitizeFileName(String fileName) {
        String cleaned = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.length() > 100 ? cleaned.substring(cleaned.length() - 100) : cleaned;
    }
}

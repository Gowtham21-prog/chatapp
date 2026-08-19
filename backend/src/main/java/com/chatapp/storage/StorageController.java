package com.chatapp.storage;

import com.chatapp.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    @PostMapping("/presigned-upload")
    public ResponseEntity<PresignedUploadResponse> createPresignedUpload(
            @Valid @RequestBody PresignedUploadRequest request) {
        return ResponseEntity.ok(
                storageService.createPresignedUpload(CurrentUser.get().userId(), request));
    }
}

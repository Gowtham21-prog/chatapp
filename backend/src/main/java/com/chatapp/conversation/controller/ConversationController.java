package com.chatapp.conversation.controller;

import com.chatapp.conversation.dto.ConversationResponse;
import com.chatapp.conversation.dto.StartConversationRequest;
import com.chatapp.conversation.service.ConversationService;
import com.chatapp.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public ResponseEntity<ConversationResponse> startOrGet(@Valid @RequestBody StartConversationRequest request) {
        return ResponseEntity.ok(
                conversationService.startOrGetDirectConversation(CurrentUser.get().userId(), request.userId()));
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> list() {
        return ResponseEntity.ok(conversationService.listForUser(CurrentUser.get().userId()));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationResponse> getById(@PathVariable UUID conversationId) {
        return ResponseEntity.ok(conversationService.getById(conversationId, CurrentUser.get().userId()));
    }
}

package com.chatapp.message.controller;

import com.chatapp.message.dto.MessageResponse;
import com.chatapp.message.service.MessageService;
import com.chatapp.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<Page<MessageResponse>> history(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(
                messageService.getHistory(conversationId, CurrentUser.get().userId(), pageRequest));
    }

    @GetMapping("/conversations/{conversationId}/messages/search")
    public ResponseEntity<Page<MessageResponse>> searchInConversation(
            @PathVariable UUID conversationId,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(
                messageService.searchInConversation(conversationId, CurrentUser.get().userId(), q, pageRequest));
    }

    @GetMapping("/messages/search")
    public ResponseEntity<Page<MessageResponse>> searchAll(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(
                messageService.searchAllConversations(CurrentUser.get().userId(), q, pageRequest));
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID messageId,
            @RequestParam(defaultValue = "false") boolean forEveryone) {

        messageService.deleteMessage(messageId, CurrentUser.get().userId(), forEveryone);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> markConversationRead(@PathVariable UUID conversationId) {
        messageService.markConversationRead(conversationId, CurrentUser.get().userId());
        return ResponseEntity.noContent().build();
    }
}

package com.chatapp.websocket;

import com.chatapp.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.Instant;

/**
 * Without this, an exception thrown inside a @MessageMapping handler just
 * disappears (STOMP has no built-in equivalent of an HTTP error response),
 * leaving the client's UI stuck with no feedback. This routes it back to
 * the sending user's private /user/queue/errors destination instead.
 */
@Controller
@Slf4j
public class StompExceptionHandler {

    @MessageExceptionHandler(ApiException.class)
    @SendToUser("/queue/errors")
    public ErrorFrame handleApiException(ApiException ex) {
        return new ErrorFrame(ex.getStatus().value(), ex.getMessage(), Instant.now());
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/queue/errors")
    public ErrorFrame handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().isEmpty()
                ? "Invalid message payload"
                : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return new ErrorFrame(400, message, Instant.now());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public ErrorFrame handleGeneric(Exception ex) {
        log.error("Unhandled WebSocket error", ex);
        return new ErrorFrame(500, "Something went wrong", Instant.now());
    }

    public record ErrorFrame(int status, String message, Instant timestamp) {
    }
}

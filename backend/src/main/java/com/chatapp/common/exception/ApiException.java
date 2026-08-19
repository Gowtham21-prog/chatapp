package com.chatapp.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all deliberately-thrown application exceptions. Carrying
 * the HttpStatus on the exception itself means the global handler doesn't
 * need a big if/else chain to map exception types to responses.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

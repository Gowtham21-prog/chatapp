package com.chatapp.anonymous.dto;

import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateAnonymousSessionRequest(
        @Size(max = 10, message = "You may select at most 10 interests")
        Set<@Size(max = 30) String> interests
) {
}

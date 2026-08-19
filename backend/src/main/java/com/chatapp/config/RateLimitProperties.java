package com.chatapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        int messagesPerMinute,
        int authAttemptsPerMinute
) {
}

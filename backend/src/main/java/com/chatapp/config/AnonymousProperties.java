package com.chatapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.anonymous")
public record AnonymousProperties(
        int sessionTtlHours,
        int matchQueuePollMs
) {
}

package com.chatapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.presence")
public record PresenceProperties(
        int heartbeatTtlSeconds
) {
}

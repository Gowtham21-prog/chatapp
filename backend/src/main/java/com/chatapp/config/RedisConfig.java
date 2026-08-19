package com.chatapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    /**
     * We deliberately use StringRedisTemplate (plain strings) rather than a
     * generic RedisTemplate with Java/JSON serializers: presence flags,
     * rate-limit counters, and matchmaking queue entries are all simple
     * strings/sets, and staying string-based keeps the data directly
     * inspectable with `redis-cli` during development and ops.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}

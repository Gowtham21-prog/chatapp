package com.chatapp.common.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fixed-window rate limiter backed by Redis INCR + EXPIRE. Simple by design:
 * a sliding-window/token-bucket algorithm would be more precise at window
 * boundaries, but fixed-window is easy to reason about, cheap (one round
 * trip), and sufficient for abuse protection on chat sends / auth attempts /
 * matchmaking requests, which is what this app needs it for.
 */
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;

    /**
     * @param key        unique key for the thing being limited, e.g. "ratelimit:auth:1.2.3.4"
     * @param maxRequests max requests allowed within the window
     * @param window     the fixed window duration
     * @return true if the caller is within the limit (and the attempt was counted), false if the limit was exceeded
     */
    public boolean tryConsume(String key, int maxRequests, Duration window) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            // Redis unavailable - fail open rather than blocking all traffic.
            return true;
        }
        if (count == 1L) {
            redisTemplate.expire(key, window);
        }
        return count <= maxRequests;
    }
}

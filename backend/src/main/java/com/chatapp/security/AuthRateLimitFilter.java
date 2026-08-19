package com.chatapp.security;

import com.chatapp.common.util.RateLimiter;
import com.chatapp.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


import java.io.IOException;
import java.time.Duration;

/**
 * Protects /api/auth/** from credential-stuffing / brute-force attempts.
 * Keyed by client IP rather than by username so an attacker can't dodge the
 * limit by cycling through usernames, and so it also throttles registration
 * spam from a single source.
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/api/auth/")) {
            String clientIp = resolveClientIp(request);
            String key = "ratelimit:auth:" + clientIp;

            boolean allowed = rateLimiter.tryConsume(
                    key, rateLimitProperties.authAttemptsPerMinute(), Duration.ofMinutes(1));

            if (!allowed) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("""
                        {"status":429,"error":"Too Many Requests","message":"Too many attempts. Please try again shortly."}""");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

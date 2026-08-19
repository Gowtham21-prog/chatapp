package com.chatapp.presence;

import com.chatapp.config.PresenceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Presence is intentionally NOT stored in Postgres. Online/offline is a
 * transient, high-churn, TTL-based fact - exactly what Redis is for. A key
 * "presence:online:{userId}" exists with a short TTL while the user has at
 * least one live WebSocket connection; the TTL is refreshed on each STOMP
 * heartbeat/frame so a crashed client (no clean disconnect) still naturally
 * falls back to "offline" once the TTL lapses, without needing a reaper job.
 *
 * We also track connection COUNT per user (a Set of session ids) so that a
 * user with the app open in two tabs doesn't flip to "offline" the moment
 * one tab closes.
 */
@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final String ONLINE_KEY_PREFIX = "presence:online:";
    private static final String SESSIONS_KEY_PREFIX = "presence:sessions:";
    private static final String LAST_SEEN_KEY_PREFIX = "presence:lastseen:";

    private final StringRedisTemplate redisTemplate;
    private final PresenceProperties presenceProperties;

    public void markOnline(UUID userId, String sessionId) {
        Duration ttl = Duration.ofSeconds(presenceProperties.heartbeatTtlSeconds());
        redisTemplate.opsForSet().add(sessionsKey(userId), sessionId);
        redisTemplate.expire(sessionsKey(userId), ttl);
        redisTemplate.opsForValue().set(onlineKey(userId), "1", ttl);
    }

    public void heartbeat(UUID userId, String sessionId) {
        markOnline(userId, sessionId);
    }

    /**
     * @return true if the user has no more live sessions (i.e. is now fully offline)
     */
    public boolean markSessionClosed(UUID userId, String sessionId) {
        redisTemplate.opsForSet().remove(sessionsKey(userId), sessionId);
        Long remaining = redisTemplate.opsForSet().size(sessionsKey(userId));
        boolean fullyOffline = remaining == null || remaining == 0;
        if (fullyOffline) {
            redisTemplate.delete(onlineKey(userId));
            redisTemplate.opsForValue().set(lastSeenKey(userId), Instant.now().toString());
        }
        return fullyOffline;
    }

    public boolean isOnline(UUID userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(onlineKey(userId)));
    }

    public Map<UUID, Boolean> isOnlineBulk(List<UUID> userIds) {
        return userIds.stream().collect(Collectors.toMap(id -> id, this::isOnline));
    }

    public Instant lastSeen(UUID userId) {
        String value = redisTemplate.opsForValue().get(lastSeenKey(userId));
        return value == null ? null : Instant.parse(value);
    }

    public Set<String> activeSessions(UUID userId) {
        return redisTemplate.opsForSet().members(sessionsKey(userId));
    }

    private String onlineKey(UUID userId) {
        return ONLINE_KEY_PREFIX + userId;
    }

    private String sessionsKey(UUID userId) {
        return SESSIONS_KEY_PREFIX + userId;
    }

    private String lastSeenKey(UUID userId) {
        return LAST_SEEN_KEY_PREFIX + userId;
    }
}

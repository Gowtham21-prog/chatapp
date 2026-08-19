package com.chatapp.anonymous.service;

import com.chatapp.config.AnonymousProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Matchmaking design:
 *
 * - "anon:waiting" is a Redis Set of session ids currently looking for a
 *   partner. "anon:interests:{sessionId}" holds that session's interest
 *   tags (a Set) so we can score overlap against other waiting sessions.
 * - Pairing is done with a Redis-backed distributed lock
 *   ("anon:match:lock", SET NX PX) around the read-candidates -> pick ->
 *   remove-both-from-queue sequence. Without the lock, two users polling
 *   at the same instant could both pick the same third user as a partner
 *   and create two rooms for one person - the lock makes "find a partner
 *   and remove exactly those two people from the queue" atomic across
 *   concurrent requests.
 * - A "room" is just an ID plus two Redis String keys mapping each
 *   sessionId -> roomId, with a TTL refreshed by activity; no room table
 *   in Postgres, consistent with anonymous sessions being fully ephemeral.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnonymousMatchmakingService {

    private static final String WAITING_SET_KEY = "anon:waiting";
    private static final String INTERESTS_KEY_PREFIX = "anon:interests:";
    private static final String ROOM_OF_SESSION_PREFIX = "anon:room-of:";
    private static final String ROOM_MEMBERS_PREFIX = "anon:room-members:";
    private static final String MATCH_LOCK_KEY = "anon:match:lock";
    private static final String SESSION_ALIVE_PREFIX = "anon:alive:";
    private static final String BLOCKED_PARTNERS_PREFIX = "anon:blocked:";

    // Atomically checks the lock value matches ours before deleting it -
    // a plain GET-then-DEL from application code has a race window where
    // the lock could expire and be re-acquired by someone else in between.
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AnonymousProperties anonymousProperties;

    public void registerSession(String sessionId, Set<String> interests) {
        Duration ttl = Duration.ofHours(anonymousProperties.sessionTtlHours());
        redisTemplate.opsForValue().set(SESSION_ALIVE_PREFIX + sessionId, "1", ttl);
        if (interests != null && !interests.isEmpty()) {
            String key = INTERESTS_KEY_PREFIX + sessionId;
            redisTemplate.opsForSet().add(key, interests.toArray(new String[0]));
            redisTemplate.expire(key, ttl);
        }
    }

    public boolean isSessionAlive(String sessionId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(SESSION_ALIVE_PREFIX + sessionId));
    }

    /**
     * Attempts to find a partner for this session right now. Returns empty
     * if no suitable partner is currently waiting (caller enqueues and
     * polls). Safe to call repeatedly/concurrently thanks to the
     * distributed lock.
     */
    public Optional<MatchResult> tryMatch(String sessionId) {
        String lockToken = UUID.randomUUID().toString();
        boolean acquired = Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(MATCH_LOCK_KEY, lockToken, Duration.ofSeconds(3)));

        if (!acquired) {
            return Optional.empty(); // someone else is matching right now; caller will poll again shortly
        }

        try {
            Set<String> rawWaiting = redisTemplate.opsForSet().members(WAITING_SET_KEY);
            if (rawWaiting == null) {
                rawWaiting = Set.of();
            }

            // Opportunistic garbage collection: since we already hold the
            // match lock and already fetched the full waiting set, prune any
            // entries whose "alive" TTL has lapsed (crashed tab, no clean
            // leave) rather than letting them accumulate forever. This is
            // "free" cleanup piggybacked on the matching pass rather than a
            // separate scheduled job.
            List<String> deadEntries = rawWaiting.stream().filter(id -> !isSessionAlive(id)).toList();
            if (!deadEntries.isEmpty()) {
                redisTemplate.opsForSet().remove(WAITING_SET_KEY, (Object[]) deadEntries.toArray(new String[0]));
            }

            Set<String> waiting = rawWaiting.stream()
                    .filter(other -> !other.equals(sessionId))
                    .filter(this::isSessionAlive)
                    .filter(other -> !isBlockedPair(sessionId, other))
                    .collect(Collectors.toSet());

            if (waiting.isEmpty()) {
                redisTemplate.opsForSet().add(WAITING_SET_KEY, sessionId);
                return Optional.empty();
            }

            Set<String> mySessionInterests = getInterests(sessionId);
            String bestPartner = null;
            int bestOverlap = -1;

            for (String candidate : waiting) {
                Set<String> candidateInterests = getInterests(candidate);
                int overlap = (int) mySessionInterests.stream().filter(candidateInterests::contains).count();
                if (overlap > bestOverlap) {
                    bestOverlap = overlap;
                    bestPartner = candidate;
                }
            }

            redisTemplate.opsForSet().remove(WAITING_SET_KEY, bestPartner);
            redisTemplate.opsForSet().remove(WAITING_SET_KEY, sessionId);

            String roomId = UUID.randomUUID().toString();
            Duration roomTtl = Duration.ofHours(anonymousProperties.sessionTtlHours());
            redisTemplate.opsForValue().set(ROOM_OF_SESSION_PREFIX + sessionId, roomId, roomTtl);
            redisTemplate.opsForValue().set(ROOM_OF_SESSION_PREFIX + bestPartner, roomId, roomTtl);
            redisTemplate.opsForSet().add(ROOM_MEMBERS_PREFIX + roomId, sessionId, bestPartner);
            redisTemplate.expire(ROOM_MEMBERS_PREFIX + roomId, roomTtl);

            Set<String> shared = new HashSet<>(mySessionInterests);
            shared.retainAll(getInterests(bestPartner));

            return Optional.of(new MatchResult(roomId, bestPartner, shared));
        } finally {
            releaseLockIfOwned(lockToken);
        }
    }

    /**
     * Called when a client polls "am I matched yet" after being enqueued by
     * a previous tryMatch call that found no one waiting.
     */
    public Optional<MatchResult> checkExistingMatch(String sessionId) {
        String roomId = redisTemplate.opsForValue().get(ROOM_OF_SESSION_PREFIX + sessionId);
        if (roomId == null) {
            return Optional.empty();
        }
        Set<String> members = redisTemplate.opsForSet().members(ROOM_MEMBERS_PREFIX + roomId);
        if (members == null) {
            return Optional.empty();
        }
        String partner = members.stream().filter(m -> !m.equals(sessionId)).findFirst().orElse(null);
        if (partner == null) {
            return Optional.empty();
        }
        Set<String> shared = new HashSet<>(getInterests(sessionId));
        shared.retainAll(getInterests(partner));
        return Optional.of(new MatchResult(roomId, partner, shared));
    }

    public void leaveQueue(String sessionId) {
        redisTemplate.opsForSet().remove(WAITING_SET_KEY, sessionId);
    }

    /**
     * Anonymous "block" is session-scoped and Redis-only (TTL matches the
     * session lifetime) - there is no persistent block row, consistent with
     * anonymous identity never touching durable storage. Blocking ends the
     * current room immediately and prevents the two sessions from being
     * re-paired for as long as either session remains alive.
     */
    public void blockPartnerAndLeave(String sessionId, String partnerSessionId) {
        Duration ttl = Duration.ofHours(anonymousProperties.sessionTtlHours());
        redisTemplate.opsForSet().add(BLOCKED_PARTNERS_PREFIX + sessionId, partnerSessionId);
        redisTemplate.expire(BLOCKED_PARTNERS_PREFIX + sessionId, ttl);
        leaveRoom(sessionId);
        leaveQueue(sessionId);
    }

    private boolean isBlockedPair(String sessionA, String sessionB) {
        Boolean aBlockedB = redisTemplate.opsForSet().isMember(BLOCKED_PARTNERS_PREFIX + sessionA, sessionB);
        Boolean bBlockedA = redisTemplate.opsForSet().isMember(BLOCKED_PARTNERS_PREFIX + sessionB, sessionA);
        return Boolean.TRUE.equals(aBlockedB) || Boolean.TRUE.equals(bBlockedA);
    }

    /**
     * Ends the current room for this session (both "Next" and disconnect
     * call this). Removes both members' room pointers so neither side is
     * left pointing at a dead room.
     */
    public Optional<String> leaveRoom(String sessionId) {
        String roomId = redisTemplate.opsForValue().get(ROOM_OF_SESSION_PREFIX + sessionId);
        if (roomId == null) {
            return Optional.empty();
        }
        Set<String> members = redisTemplate.opsForSet().members(ROOM_MEMBERS_PREFIX + roomId);
        redisTemplate.delete(ROOM_OF_SESSION_PREFIX + sessionId);
        if (members != null) {
            for (String member : members) {
                redisTemplate.delete(ROOM_OF_SESSION_PREFIX + member);
            }
        }
        redisTemplate.delete(ROOM_MEMBERS_PREFIX + roomId);
        return Optional.of(roomId);
    }

    public Optional<String> partnerSessionId(String sessionId, String roomId) {
        Set<String> members = redisTemplate.opsForSet().members(ROOM_MEMBERS_PREFIX + roomId);
        if (members == null) {
            return Optional.empty();
        }
        return members.stream().filter(m -> !m.equals(sessionId)).findFirst();
    }

    public Optional<String> currentRoomOf(String sessionId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(ROOM_OF_SESSION_PREFIX + sessionId));
    }

    private Set<String> getInterests(String sessionId) {
        Set<String> interests = redisTemplate.opsForSet().members(INTERESTS_KEY_PREFIX + sessionId);
        return interests == null ? Set.of() : interests;
    }

    private void releaseLockIfOwned(String lockToken) {
        redisTemplate.execute(RELEASE_LOCK_SCRIPT, java.util.List.of(MATCH_LOCK_KEY), lockToken);
    }

    public record MatchResult(String roomId, String partnerSessionId, Set<String> sharedInterests) {
    }
}

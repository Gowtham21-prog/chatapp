# Architecture

This document explains the significant design decisions and why they were made,
plus the bugs caught during hand-review (since this code was never compiled — see
main README).

## Layered backend architecture

Each domain (`auth`, `user`, `conversation`, `message`, `moderation`, `notification`,
`presence`, `storage`, `anonymous`) is a vertical slice with its own
`entity/repository/service/controller/dto` packages, rather than horizontal layers
(`controllers/`, `services/`, ...) spanning all domains. Shared infrastructure
(`config`, `common`, `security`, `websocket`) sits alongside. This was chosen so each
stage of the build (auth → profiles/messaging → real-time → files/notifications →
anonymous mode) was additive — later stages didn't need to touch earlier ones except
at clearly-scoped integration points (e.g. `ChatWebSocketController` calling into
`MessageService` and `NotificationService`).

## Why conversations are participant-based, not `user_a`/`user_b` columns

`Conversation` has a `ConversationParticipant` join table rather than two direct user-id
columns. For 1-to-1 chat this costs one extra join. The payoff: group chat (explicitly
out of scope for this build, but a natural next feature) becomes "add more participant
rows," not a schema migration and a service-layer rewrite. A deterministic
`direct_pair_key` (`min(userA,userB)_max(userA,userB)`) plus a partial unique index is
what actually enforces "only one direct conversation per pair" — the participant table
alone wouldn't prevent duplicates.

## Why message sending is WebSocket-only, not also REST

`MessageService.send()` is only ever called from `ChatWebSocketController`. There's no
`POST /api/messages` endpoint. This was a deliberate constraint, not an oversight: a
REST send endpoint would create two divergent code paths for "a message was created,"
and it's easy for a REST-only client to silently skip real-time delivery to the other
participant. Collapsing to one path removes that failure mode entirely. History,
search, and delete remain REST because those are naturally request/response, not
event-driven.

## WebSocket security model

Authentication happens once, on the STOMP `CONNECT` frame, via
`StompAuthChannelInterceptor`. A JWT (or anonymous session token — see below) is
validated there and the resulting principal is attached to the STOMP session for its
whole lifetime. This means:
- An unauthenticated client cannot open a session at all — rejection happens before
  any subscription or send is possible.
- Individual `SEND` frames don't re-validate a token; they trust the session's attached
  principal, the same way an HTTP session trusts its established auth after login.

One real bug caught during review: **Spring's STOMP support does not auto-wire Bean
Validation on `@Payload` arguments** the way Spring MVC does for `@RequestBody`. Simply
adding `@Valid` in `ChatWebSocketController` would have silently done nothing. Fixed by
registering an explicit `Validator` bean via
`WebSocketMessageBrokerConfigurer.getValidator()` and adding a
`MethodArgumentNotValidException` handler in `StompExceptionHandler` so validation
failures actually surface to the client instead of failing silently.

## Two independent auth schemes, one WebSocket endpoint

Registered users and anonymous chat participants share the same `/ws` STOMP endpoint
but authenticate completely differently: registered users present a normal JWT
(validated by `JwtService`); anonymous participants present a separately-typed signed
token (validated by `AnonymousTokenService`) tied to a Redis-backed session, never to a
`users` row. The client declares which scheme it's using via a `mode: anonymous` STOMP
header so the interceptor doesn't have to guess by trying both validators against every
token. The two token types are cryptographically distinguishable (different `type`
claim) so an anonymous token can never be escalated into treatment as a registered
user's token, even if someone tried.

## Anonymous chat: why nothing touches Postgres

The brief calls for "temporary anonymous sessions that are not permanently tied to an
identity." That's implemented literally: there is no `anonymous_sessions` table, and
anonymous chat messages are **never persisted anywhere** — `AnonymousWebSocketController`
relays a message directly from one STOMP session to the other via
`convertAndSendToUser`, with no database write in between. Everything about an
anonymous session — its existence, its interest tags, its current room, its blocks —
lives in Redis with a TTL matching the session lifetime. When Redis keys expire, the
session is gone, with nothing left to retain.

Reports against an anonymous participant are the one exception that touches Postgres
(the `reports` table), and even there, the anonymous side is referenced by a bare
session-id string, never a foreign key — because by the time a moderator reviews a
report, the session that generated it may well no longer exist.

## Matchmaking algorithm

`AnonymousMatchmakingService` maintains a Redis Set of waiting session ids
(`anon:waiting`) and a per-session interest Set. When a session asks to be matched:

1. Acquire a short-lived distributed lock (`SET NX PX` on `anon:match:lock`).
2. Read all currently-waiting sessions, filtering out dead ones and ones this session
   has blocked (or that have blocked this session).
3. Score each candidate by interest overlap; pick the best match (ties broken
   arbitrarily).
4. Remove both sessions from the waiting set and create a "room" (just an id plus two
   Redis pointers, no DB row).
5. Release the lock.

The lock exists because without it, two sessions polling for a match in the same
instant could both independently select the same third waiting session as their
partner — creating two rooms for one person, with one side never finding out. The lock
makes "pick a partner and atomically remove exactly those two people from the queue" a
single indivisible operation.

**Bug caught during review:** the initial lock-release implementation was
GET-then-compare-then-DEL from application code — three separate round trips with a
race window between them where the lock could expire and be re-acquired by a different
caller, and then get deleted out from under that new owner. Fixed by moving the
check-and-delete into a single atomic Lua script (`EVAL`), which is the standard
correct pattern for "release a lock only if I still own it" in Redis.

## Presence: multi-session aware, TTL-based, no reaper job

`PresenceService` tracks not just "is this user online" but *how many active WebSocket
sessions* they have (a Redis Set of session ids per user). A user is "online" from
their first connected tab/device and stays online until their *last* one disconnects —
closing one of two open tabs doesn't flip them offline. The online flag itself has a
TTL refreshed by a periodic heartbeat frame from the client; if a connection drops
uncleanly (crashed tab, lost network) with no `DISCONNECT` frame ever received, the
TTL simply lapses and the user falls back to offline on its own, with no separate
cleanup/reaper process needed.

## File sharing: presigned URLs, not proxied uploads

`StorageService` issues short-lived presigned S3 `PUT` URLs; the browser uploads
directly to MinIO/S3, and downloads happen directly from S3's public URL too. The
Spring Boot process never has file bytes pass through it. This is the standard pattern
for file sharing at any real scale, and it also means the same code works unmodified
against real AWS S3 in production — only `S3_ENDPOINT` and credentials change.

## Rate limiting

A single reusable `RateLimiter` (fixed-window, Redis `INCR` + `EXPIRE`) backs three
separate limits: auth attempts (by IP, via a servlet filter), WebSocket message sends
(by user id, inside `ChatWebSocketController`), and anonymous session creation (by IP).
Fixed-window was chosen over a more precise sliding-window/token-bucket algorithm
because it's a single Redis round trip, easy to reason about, and sufficient for abuse
protection at this scale — precision at window boundaries matters much more for
billing-grade rate limiting than for "stop someone from spamming the send button."

## Frontend: one ChatSocket per session, not per component

`ChatSocketProvider` creates exactly one `ChatSocket` instance for the lifetime of a
login session (reconnecting only when the access token itself changes), and exposes it
via context. Components subscribe/unsubscribe to specific event types
(`onMessage`, `onTyping`, etc.) rather than each opening their own WebSocket connection
— this avoids the classic bug of duplicate connections per open chat window/component
remount, and keeps reconnection logic in exactly one place.

Anonymous chat deliberately uses a *separate* socket class (`AnonymousChatSocket`)
rather than a shared base class with a mode flag — the two auth schemes and message
destinations don't overlap enough to make sharing worthwhile; two small focused classes
were judged clearer than one with conditional branches throughout.

# ChatApp — Real-Time Chat Platform

A full-stack real-time chat platform: registered-user 1-to-1 messaging with presence,
typing indicators, read receipts, file sharing, and search — plus a separate anonymous
"random chat" mode with interest-based matchmaking, inspired by Omegle-style platforms.

**Stack:** React + TypeScript (frontend) · Spring Boot 3 / Java 21 (backend) ·
PostgreSQL (durable data) · Redis (presence, rate limiting, anonymous matchmaking) ·
MinIO / S3 (file storage) · STOMP over WebSocket (real-time transport)

---

## ⚠️ Before you trust this code: verification status

This project was built in a sandboxed environment **without Maven, npm registry access,
or the ability to run a JVM + Postgres + Redis + browser simultaneously**. That means:

- The backend has **never been compiled**. It was written and hand-reviewed carefully
  (including catching and fixing two real bugs during review — see
  [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — but `mvn clean verify` has not
  actually been run.
- The frontend has **never been built**. Same situation: `npm install && npm run build`
  has not been executed.
- **No feature has been clicked through in a real browser.**

**Your first step should be running the test/build commands below and fixing whatever
the compiler/test runner surfaces.** Treat this as a thorough first draft that needs a
real build-and-test pass, not as verified working software. I'd estimate the risk is in
small things — an import, a method signature mismatch, a Lombok annotation edge case —
rather than architectural problems, since the design was reviewed carefully throughout,
but there will very likely be *something*.

---

## Quick start

### 1. Start infrastructure

```bash
cp .env.example .env
docker compose up -d
```

This starts Postgres, Redis, and MinIO (with the upload bucket auto-created).

### 2. Run the backend

```bash
cd backend
cp ../.env.example .env   # or export the same variables in your shell
mvn spring-boot:run
```

The backend runs on `http://localhost:8080`. Flyway migrations run automatically on
startup. First things to check if it doesn't start cleanly:
- Postgres/Redis actually up (`docker compose ps`)
- Java 21 on your PATH (`java -version`)
- `mvn clean verify` first, to isolate compile/test failures from runtime config issues

### 3. Run the backend tests

```bash
cd backend
mvn clean verify
```

Integration tests spin up **real** Postgres and Redis containers via Testcontainers
(requires Docker running). This is the primary correctness gate for this project —
run it before trusting any backend behavior.

### 4. Run the frontend

```bash
cd frontend
npm install
cp .env.example .env
npm run dev
```

Runs on `http://localhost:5173`. Open it, register an account, and open a second
browser (or incognito window) with a second account to test real-time messaging
between two users.

### 5. Try anonymous chat

Navigate to `/anonymous` (linked from the login page). No account needed — open two
browser tabs/windows there to get matched with "yourself" for testing.

---

## Project structure

```
chatapp/
├── docker-compose.yml       # Postgres, Redis, MinIO for local dev
├── .env.example              # every configurable value, documented
├── backend/                  # Spring Boot API + WebSocket server
│   └── src/main/java/com/chatapp/
│       ├── auth/              # register, login, refresh, logout
│       ├── user/               # profiles, search
│       ├── conversation/       # 1-to-1 conversation creation/listing
│       ├── message/             # send, history, search, delete, receipts
│       ├── moderation/          # blocking, reporting
│       ├── notification/        # persisted + pushed notifications
│       ├── presence/             # Redis online/offline tracking
│       ├── storage/               # presigned S3/MinIO upload URLs
│       ├── anonymous/              # ephemeral sessions + Redis matchmaking
│       ├── websocket/               # STOMP config, auth, controllers
│       ├── security/                 # JWT, filters, rate limiting
│       ├── config/                    # typed @ConfigurationProperties, CORS, beans
│       └── common/                     # shared exceptions, DTOs, RateLimiter
└── frontend/                 # React + TypeScript + Tailwind SPA
    └── src/
        ├── api/                # REST client modules (one per backend domain)
        ├── lib/                 # axios client, STOMP socket wrappers
        ├── store/                # Zustand auth store
        ├── components/            # ui/, chat/, notifications/, layout/
        ├── pages/                  # Login, Register, Chat, AnonymousChat
        └── types/                   # TypeScript types mirroring backend DTOs
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the reasoning behind the
significant design decisions (why conversations are participant-based, why anonymous
chat never touches Postgres, how WebSocket auth works, the matchmaking algorithm, etc.)

---

## Feature checklist

**Registered-user chat**
- [x] Registration/login with JWT (short-lived access token + rotating opaque refresh token)
- [x] User profiles (display name, bio, avatar)
- [x] 1-to-1 conversations (idempotent creation, one conversation per user pair)
- [x] Real-time messaging over STOMP/WebSocket
- [x] Online/offline presence (multi-tab aware, Redis TTL-based)
- [x] Typing indicators
- [x] Sent → Delivered → Read message states
- [x] Message history with pagination
- [x] Message search (per-conversation and global)
- [x] Message deletion (soft delete; "for me" vs "for everyone" within 48h)
- [x] Image/file sharing via presigned direct-to-S3/MinIO upload
- [x] Notifications (persisted + live-pushed when recipient is offline)
- [x] Blocking and reporting

**Anonymous chat**
- [x] No account required
- [x] Optional interest tags, used for match scoring
- [x] Redis-backed matchmaking queue with atomic pairing (distributed lock)
- [x] Real-time chat over a parallel WebSocket auth path
- [x] "Next" to leave and re-queue
- [x] Session-scoped blocking (Redis, not persisted)
- [x] Reporting (references the ephemeral session id, not any account)
- [x] Messages are **never persisted** — relayed socket-to-socket only

**Cross-cutting**
- [x] Input validation (Bean Validation on REST, custom validator wired into STOMP)
- [x] Global exception handling → consistent `ApiError` JSON shape
- [x] CORS configured from one shared source of truth
- [x] Rate limiting (auth attempts, message sends, anonymous session creation) via Redis
- [x] Environment-variable-driven configuration throughout
- [x] Responsive, mobile-first UI with loading/empty/error states

---

## Known limitations / things to do before production

- **Single-instance WebSocket broker.** The STOMP broker is Spring's built-in
  simple in-memory broker, not an external one (RabbitMQ/ActiveMQ). This is fine for
  one backend instance; horizontally scaling would require switching to a STOMP relay
  so real-time messages fan out across instances.
- **No message content moderation/profanity filtering** beyond block/report — this
  was out of scope for the brief but would be a reasonable next step.
- **No push notifications to native mobile** — the "notifications" feature is
  in-app (persisted + WebSocket-pushed), not APNs/FCM.
- **No automated frontend tests** — the backend has real integration tests
  (Testcontainers); the frontend does not yet have equivalent coverage.
- **No admin/moderation dashboard** for reviewing reports — reports are persisted
  and queryable in the database but there's no UI for a moderator to act on them.
- **Group chat is schema-ready but not implemented.** Conversations use a generic
  participant model specifically so this would be additive, not a rewrite.

---

## Environment variables

All configuration is environment-variable driven — see [`.env.example`](.env.example)
for the full list with comments. Highlights:

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | Signs both registered-user and anonymous-session tokens. **Change this in any real deployment.** |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Postgres connection |
| `SPRING_REDIS_HOST` / `_PORT` | Redis connection |
| `S3_ENDPOINT` / `S3_BUCKET` / `S3_PUBLIC_URL` | Points at MinIO locally; point at real AWS S3 in production with no code change |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list; shared by REST CORS config and the WebSocket handshake |
| `VITE_API_BASE_URL` / `VITE_WS_URL` | Frontend's backend addresses |

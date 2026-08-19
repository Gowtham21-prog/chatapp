CREATE TABLE user_blocks (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_user_blocks UNIQUE (blocker_id, blocked_id),
    CONSTRAINT chk_user_blocks_not_self CHECK (blocker_id <> blocked_id)
);

CREATE INDEX idx_user_blocks_blocker ON user_blocks (blocker_id);
CREATE INDEX idx_user_blocks_blocked ON user_blocks (blocked_id);

-- Reports can reference either a registered reported user or an anonymous
-- session id (string, not FK - anonymous sessions are transient/Redis-backed
-- and may already be gone by the time a report is reviewed).
CREATE TABLE reports (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_user_id      UUID REFERENCES users (id) ON DELETE SET NULL,
    reporter_anonymous_id VARCHAR(64),
    reported_user_id      UUID REFERENCES users (id) ON DELETE SET NULL,
    reported_anonymous_id VARCHAR(64),
    context               VARCHAR(20) NOT NULL DEFAULT 'DIRECT', -- DIRECT, ANONYMOUS
    reason                VARCHAR(40) NOT NULL, -- SPAM, HARASSMENT, NUDITY, HATE_SPEECH, OTHER
    details               VARCHAR(1000),
    message_id            UUID REFERENCES messages (id) ON DELETE SET NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN, REVIEWED, DISMISSED, ACTIONED
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_reports_reporter_present
        CHECK (reporter_user_id IS NOT NULL OR reporter_anonymous_id IS NOT NULL),
    CONSTRAINT chk_reports_reported_present
        CHECK (reported_user_id IS NOT NULL OR reported_anonymous_id IS NOT NULL)
);

CREATE INDEX idx_reports_reported_user ON reports (reported_user_id);
CREATE INDEX idx_reports_reported_anonymous ON reports (reported_anonymous_id);
CREATE INDEX idx_reports_status ON reports (status);

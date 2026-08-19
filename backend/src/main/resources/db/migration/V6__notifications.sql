CREATE TABLE notifications (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type         VARCHAR(30) NOT NULL, -- NEW_MESSAGE, MENTION, SYSTEM
    title        VARCHAR(200) NOT NULL,
    body         VARCHAR(500),
    metadata     JSONB,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_unread ON notifications (user_id, read_at);
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);

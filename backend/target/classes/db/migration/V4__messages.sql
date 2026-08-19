CREATE TABLE messages (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id    UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content            TEXT,                      -- nullable: attachment-only messages
    message_type       VARCHAR(20) NOT NULL DEFAULT 'TEXT', -- TEXT, IMAGE, FILE
    attachment_url      VARCHAR(1000),
    attachment_name      VARCHAR(255),
    attachment_size_bytes BIGINT,
    attachment_mime_type  VARCHAR(120),
    status             VARCHAR(20) NOT NULL DEFAULT 'SENT', -- SENT, DELIVERED, READ
    deleted_at         TIMESTAMPTZ,               -- soft delete: preserves history integrity for the other party
    deleted_for_everyone BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_message_has_content CHECK (content IS NOT NULL OR attachment_url IS NOT NULL)
);

CREATE INDEX idx_messages_conversation_created ON messages (conversation_id, created_at DESC);
CREATE INDEX idx_messages_sender ON messages (sender_id);
-- Full text search over message content for the search feature.
CREATE INDEX idx_messages_content_trgm ON messages USING gin (to_tsvector('english', coalesce(content, '')));

-- Per-recipient delivery/read tracking. For 1-to-1 this is one row per
-- message (the other participant), but the shape scales to group chat
-- without a migration.
CREATE TABLE message_receipts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id    UUID NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    delivered_at  TIMESTAMPTZ,
    read_at       TIMESTAMPTZ,

    CONSTRAINT uq_message_receipt UNIQUE (message_id, user_id)
);

CREATE INDEX idx_message_receipts_user ON message_receipts (user_id);

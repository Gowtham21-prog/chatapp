-- We model conversations generically (conversation + participants) rather
-- than a hardcoded user_a/user_b pair on the conversation row itself. This
-- costs one extra join for 1-to-1 chat but means group chat is a additive
-- feature later (more participant rows) rather than a schema rewrite.
CREATE TABLE conversations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type        VARCHAR(20) NOT NULL DEFAULT 'DIRECT', -- DIRECT (group reserved for future use)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conversation_participants (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id   UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id           UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_read_at      TIMESTAMPTZ,
    is_archived       BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_conversation_participant UNIQUE (conversation_id, user_id)
);

CREATE INDEX idx_conversation_participants_user ON conversation_participants (user_id);
CREATE INDEX idx_conversation_participants_conversation ON conversation_participants (conversation_id);

-- Enforces that a DIRECT conversation can never exist twice between the
-- same two users: we compute a deterministic pair key (least/greatest user
-- id) in the application layer and store it here for a unique constraint.
ALTER TABLE conversations ADD COLUMN direct_pair_key VARCHAR(73); -- "<uuid>_<uuid>"
CREATE UNIQUE INDEX uq_conversations_direct_pair_key
    ON conversations (direct_pair_key)
    WHERE direct_pair_key IS NOT NULL;

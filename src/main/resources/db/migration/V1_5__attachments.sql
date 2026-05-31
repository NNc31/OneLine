CREATE TABLE attachments (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    participant_id BIGINT NOT NULL,
    object_key TEXT NOT NULL UNIQUE,
    ciphertext_size BIGINT NOT NULL,
    confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_attachments_chat FOREIGN KEY (chat_id) REFERENCES chats (id) ON DELETE CASCADE,
    CONSTRAINT fk_attachments_participant FOREIGN KEY (participant_id) REFERENCES chat_participants (id) ON DELETE CASCADE
);

CREATE INDEX idx_attachments_chat_id ON attachments (chat_id);

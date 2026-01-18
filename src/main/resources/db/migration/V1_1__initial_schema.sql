CREATE TABLE chats (
    id                  BIGSERIAL PRIMARY KEY,
    chat_token_hash     BYTEA NOT NULL UNIQUE,
    name                VARCHAR(120),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at           TIMESTAMPTZ
);

CREATE TABLE chat_participants (
    id                      BIGSERIAL PRIMARY KEY,
    chat_id                 BIGINT NOT NULL,
    session_token_hash      BYTEA NOT NULL UNIQUE,
    display_name            VARCHAR(40) NOT NULL,
    joined_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_chat_participants_chat FOREIGN KEY (chat_id) REFERENCES chats (id) ON DELETE CASCADE
);

CREATE INDEX idx_chat_participants_chat_name_seen ON chat_participants (chat_id, display_name, last_seen_at);

CREATE TABLE messages (
    id                  BIGSERIAL PRIMARY KEY,
    chat_id             BIGINT NOT NULL,
    participant_id      BIGINT NOT NULL,
    client_message_id   UUID NOT NULL,
    content             BYTEA NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_messages_chat FOREIGN KEY (chat_id) REFERENCES chats (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_participant FOREIGN KEY (participant_id) REFERENCES chat_participants (id) ON DELETE RESTRICT,
    CONSTRAINT uq_messages_chat_client_id UNIQUE (chat_id, client_message_id)
);

CREATE INDEX idx_messages_chat_id ON messages (chat_id, id DESC);

CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    role        VARCHAR(32) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chats (
    id              BIGSERIAL PRIMARY KEY,
    access_token    VARCHAR(255) NOT NULL UNIQUE,
    inviter_id      BIGINT NOT NULL,
    invitee_id      BIGINT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_chats_inviter FOREIGN KEY (inviter_id) REFERENCES users (id),
    CONSTRAINT fk_chats_invitee FOREIGN KEY (invitee_id) REFERENCES users (id),
    CONSTRAINT chk_chats_different_users CHECK (inviter_id <> invitee_id)
);

CREATE TABLE messages (
    id          BIGSERIAL PRIMARY KEY,
    chat_id     BIGINT NOT NULL,
    sender_id   BIGINT NOT NULL,
    content     VARCHAR(2000) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_messages_chat FOREIGN KEY (chat_id) REFERENCES chats (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id)
);

CREATE INDEX idx_chats_access_token ON chats (access_token);
CREATE INDEX idx_messages_chat_created ON messages (chat_id, created_at);

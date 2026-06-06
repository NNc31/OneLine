CREATE TABLE attachment_chunks (
    id BIGSERIAL PRIMARY KEY,
    attachment_id BIGINT NOT NULL,
    chunk_index INTEGER NOT NULL,
    object_key TEXT NOT NULL UNIQUE,
    ciphertext_size BIGINT NOT NULL,

    CONSTRAINT fk_attachment_chunks_attachment FOREIGN KEY (attachment_id) REFERENCES attachments (id) ON DELETE CASCADE,
    CONSTRAINT uq_attachment_chunks_index UNIQUE (attachment_id, chunk_index)
);

CREATE INDEX idx_attachment_chunks_attachment_id ON attachment_chunks (attachment_id);

ALTER TABLE attachments ALTER COLUMN object_key DROP NOT NULL;

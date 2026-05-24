CREATE INDEX idx_messages_participant_id ON messages (participant_id);
ALTER TABLE messages DROP CONSTRAINT fk_messages_participant;
ALTER TABLE messages ADD CONSTRAINT fk_messages_participant FOREIGN KEY (participant_id) REFERENCES chat_participants (id) ON DELETE CASCADE;

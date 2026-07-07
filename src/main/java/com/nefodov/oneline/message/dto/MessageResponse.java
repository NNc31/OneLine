package com.nefodov.oneline.message.dto;

import com.nefodov.oneline.message.Message;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public record MessageResponse(
        Long id,
        Long participantId,
        String displayName,
        byte[] content,
        Instant createdAt,
        String type
) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getParticipant().getId(),
                message.getParticipant().getDisplayName(),
                message.getContent(),
                message.getCreatedAt(),
                message.getType());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageResponse(Long id1, Long participantId1, String name, byte[] content1, Instant at, String type1))) return false;
        return Objects.equals(id, id1)
                && Objects.equals(participantId, participantId1)
                && Objects.equals(displayName, name)
                && Arrays.equals(content, content1)
                && Objects.equals(createdAt, at)
                && Objects.equals(type, type1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, participantId, displayName, Arrays.hashCode(content), createdAt, type);
    }

    @Override
    public String toString() {
        return "MessageResponse[id=" + id
                + ", participantId=" + participantId
                + ", displayName=" + displayName
                + ", content=" + (content == null ? "null" : "byte[" + content.length + "]")
                + ", createdAt=" + createdAt
                + ", type=" + type + "]";
    }
}

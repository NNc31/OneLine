package com.nefodov.oneline.message.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record SendMessageRequest(
        @NotNull UUID clientMessageId,
        @NotNull @Size(max = 8192) byte[] content
) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SendMessageRequest other)) return false;
        return Objects.equals(clientMessageId, other.clientMessageId) && Arrays.equals(content, other.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientMessageId, Arrays.hashCode(content));
    }

    @Override
    public String toString() {
        return "SendMessageRequest[clientMessageId=" + clientMessageId + ", content=" + (content == null ? "null" : "byte[" + content.length + "]") + "]";
    }
}

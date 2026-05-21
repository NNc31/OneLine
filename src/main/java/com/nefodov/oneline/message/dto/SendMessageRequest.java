package com.nefodov.oneline.message.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendMessageRequest(
        @NotNull UUID clientMessageId,
        @NotNull @Size(max = 8192) byte[] content
) {
}

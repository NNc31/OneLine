package com.nefodov.oneline.message.dto;

import java.time.Instant;

public record MessageResponse(
        Long id,
        Long participantId,
        String displayName,
        byte[] content,
        Instant createdAt
) {
}

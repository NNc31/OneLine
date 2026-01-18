package com.nefodov.oneline.message.dto;

import java.time.Instant;

public record MessageResponse(
        Long id,
        Long participantId,
        String displayName,
        String content,
        Instant createdAt
) {
}

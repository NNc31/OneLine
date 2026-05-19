package com.nefodov.oneline.message.dto;

import java.util.UUID;

public record SendMessageRequest(UUID clientMessageId, byte[] content) {
}

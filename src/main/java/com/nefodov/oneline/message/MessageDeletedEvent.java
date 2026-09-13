package com.nefodov.oneline.message;

public record MessageDeletedEvent(Long chatId, Long messageId) {
}

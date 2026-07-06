package com.nefodov.oneline.chat;

public record ParticipantJoinedEvent(Long chatId, Long participantId, String displayName) {
}

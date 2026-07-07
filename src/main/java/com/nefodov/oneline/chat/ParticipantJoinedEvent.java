package com.nefodov.oneline.chat;

import com.nefodov.oneline.message.dto.MessageResponse;

public record ParticipantJoinedEvent(Long chatId, MessageResponse message) {
}

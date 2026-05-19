package com.nefodov.oneline.chat.dto;

public record ChatMetaResponse(Long chatId, long participantsCount, ParticipantView me) {
}

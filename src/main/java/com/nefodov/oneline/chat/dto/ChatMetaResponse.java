package com.nefodov.oneline.chat.dto;

public record ChatMetaResponse(Long chatId, String name, long participantsCount, ParticipantView me) {
}

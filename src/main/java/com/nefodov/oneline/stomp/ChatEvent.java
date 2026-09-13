package com.nefodov.oneline.stomp;

import com.nefodov.oneline.chat.dto.ParticipantView;

import java.util.List;

public record ChatEvent(String type, List<ParticipantView> online, ParticipantView participant, boolean typing, Long messageId) {

    public static ChatEvent presence(List<ParticipantView> online) {
        return new ChatEvent("presence", online, null, false, null);
    }

    public static ChatEvent typing(ParticipantView participant, boolean typing) {
        return new ChatEvent("typing", null, participant, typing, null);
    }

    public static ChatEvent deleted(Long messageId) {
        return new ChatEvent("deleted", null, null, false, messageId);
    }
}

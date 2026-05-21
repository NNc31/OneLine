package com.nefodov.oneline.messaging;

import com.nefodov.oneline.chat.dto.ParticipantView;

import java.util.List;

public record ChatEvent(String type, List<ParticipantView> online, ParticipantView participant, boolean typing) {

    public static ChatEvent presence(List<ParticipantView> online) {
        return new ChatEvent("presence", online, null, false);
    }

    public static ChatEvent typing(ParticipantView participant, boolean typing) {
        return new ChatEvent("typing", null, participant, typing);
    }
}

package com.nefodov.oneline.stomp;

import com.nefodov.oneline.message.dto.MessageResponse;

public interface ChatBroadcaster {

    String TOPIC_PREFIX = "/topic/chat.";
    String EVENTS_SUFFIX = ".events";

    void broadcast(Long chatId, MessageResponse message);
    void broadcastEvent(Long chatId, ChatEvent event);
}

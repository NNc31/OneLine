package com.nefodov.oneline.messaging;

import com.nefodov.oneline.message.dto.MessageResponse;

public interface ChatBroadcaster {

    String TOPIC_PREFIX = "/topic/chat.";

    void broadcast(Long chatId, MessageResponse message);
}

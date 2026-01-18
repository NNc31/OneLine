package com.nefodov.oneline.messaging;

import com.nefodov.oneline.message.dto.MessageResponse;

public interface ChatBroadcaster {

    void broadcast(Long chatId, MessageResponse message);
}

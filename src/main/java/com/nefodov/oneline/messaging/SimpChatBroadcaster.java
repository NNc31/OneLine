package com.nefodov.oneline.messaging;

import com.nefodov.oneline.message.dto.MessageResponse;
import lombok.AllArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class SimpChatBroadcaster implements ChatBroadcaster {

    static final String TOPIC_PREFIX = "/topic/chat.";

    private final SimpMessagingTemplate template;

    @Override
    public void broadcast(Long chatId, MessageResponse message) {
        template.convertAndSend(TOPIC_PREFIX + chatId, message);
    }
}

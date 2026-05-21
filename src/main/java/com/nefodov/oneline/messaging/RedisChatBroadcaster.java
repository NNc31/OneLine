package com.nefodov.oneline.messaging;

import com.nefodov.oneline.message.dto.MessageResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RedisChatBroadcaster implements ChatBroadcaster {

    static final String CHANNEL = "oneline.chat.broadcast";
    static final String EVENTS_CHANNEL = "oneline.chat.events";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public RedisChatBroadcaster(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void broadcast(Long chatId, MessageResponse message) {
        publish(CHANNEL, new MessageEnvelope(chatId, message));
    }

    @Override
    public void broadcastEvent(Long chatId, ChatEvent event) {
        publish(EVENTS_CHANNEL, new EventEnvelope(chatId, event));
    }

    private void publish(String channel, Object envelope) {
        try {
            redis.convertAndSend(channel, jsonMapper.writeValueAsString(envelope));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize broadcast envelope", e);
        }
    }

    public record MessageEnvelope(Long chatId, MessageResponse message) {
    }

    public record EventEnvelope(Long chatId, ChatEvent event) {
    }
}

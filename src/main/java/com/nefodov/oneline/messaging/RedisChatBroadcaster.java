package com.nefodov.oneline.messaging;

import com.nefodov.oneline.message.dto.MessageResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RedisChatBroadcaster implements ChatBroadcaster {

    static final String CHANNEL = "oneline.chat.broadcast";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public RedisChatBroadcaster(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void broadcast(Long chatId, MessageResponse message) {
        try {
            BroadcastEnvelope envelope = new BroadcastEnvelope(chatId, message);
            redis.convertAndSend(CHANNEL, jsonMapper.writeValueAsString(envelope));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize broadcast envelope", e);
        }
    }

    public record BroadcastEnvelope(Long chatId, MessageResponse message) {
    }
}

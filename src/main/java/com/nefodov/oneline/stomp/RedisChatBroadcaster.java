package com.nefodov.oneline.stomp;

import com.nefodov.oneline.message.dto.MessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
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
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, skipping broadcast to {}: {}", channel, e.getMessage());
        } catch (JacksonException e) {
            log.warn("Failed to serialize broadcast envelope for {}: {}", channel, e.getMessage());
        }
    }

    public record MessageEnvelope(Long chatId, MessageResponse message) {
    }

    public record EventEnvelope(Long chatId, ChatEvent event) {
    }
}

package com.nefodov.oneline.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Configuration
public class RedisBroadcastSubscriber {

    @Bean
    public RedisMessageListenerContainer redisBroadcastListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter redisMessageListenerAdapter,
            MessageListenerAdapter redisEventListenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(redisMessageListenerAdapter, new ChannelTopic(RedisChatBroadcaster.CHANNEL));
        container.addMessageListener(redisEventListenerAdapter, new ChannelTopic(RedisChatBroadcaster.EVENTS_CHANNEL));
        return container;
    }

    @Bean
    public MessageListenerAdapter redisMessageListenerAdapter(Receiver receiver) {
        return new MessageListenerAdapter(receiver, "onMessage");
    }

    @Bean
    public MessageListenerAdapter redisEventListenerAdapter(Receiver receiver) {
        return new MessageListenerAdapter(receiver, "onEvent");
    }

    @Component
    public static class Receiver {

        private final SimpMessagingTemplate simp;
        private final JsonMapper jsonMapper;

        public Receiver(SimpMessagingTemplate simp, JsonMapper jsonMapper) {
            this.simp = simp;
            this.jsonMapper = jsonMapper;
        }

        public void onMessage(String payload) {
            try {
                RedisChatBroadcaster.MessageEnvelope envelope =
                        jsonMapper.readValue(payload, RedisChatBroadcaster.MessageEnvelope.class);
                simp.convertAndSend(ChatBroadcaster.TOPIC_PREFIX + envelope.chatId(), envelope.message());
            } catch (JacksonException e) {
                log.warn("Could not decode message envelope: {}", e.getMessage());
            }
        }

        public void onEvent(String payload) {
            try {
                RedisChatBroadcaster.EventEnvelope envelope =
                        jsonMapper.readValue(payload, RedisChatBroadcaster.EventEnvelope.class);
                simp.convertAndSend(
                        ChatBroadcaster.TOPIC_PREFIX + envelope.chatId() + ChatBroadcaster.EVENTS_SUFFIX,
                        envelope.event());
            } catch (JacksonException e) {
                log.warn("Could not decode event envelope: {}", e.getMessage());
            }
        }
    }
}

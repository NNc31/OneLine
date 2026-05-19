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
    public RedisMessageListenerContainer redisBroadcastListenerContainer(RedisConnectionFactory connectionFactory,
                                                                         MessageListenerAdapter redisBroadcastListenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(redisBroadcastListenerAdapter, new ChannelTopic(RedisChatBroadcaster.CHANNEL));
        return container;
    }

    @Bean
    public MessageListenerAdapter redisBroadcastListenerAdapter(Receiver receiver) {
        return new MessageListenerAdapter(receiver, "onMessage");
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
                RedisChatBroadcaster.BroadcastEnvelope envelope = jsonMapper.readValue(payload, RedisChatBroadcaster.BroadcastEnvelope.class);
                simp.convertAndSend(ChatBroadcaster.TOPIC_PREFIX + envelope.chatId(), envelope.message());
            } catch (JacksonException e) {
                log.warn("Could not decode broadcast envelope: {}", e.getMessage());
            }
        }
    }
}

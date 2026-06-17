package com.nefodov.oneline.config;

import com.nefodov.oneline.chat.dto.ParticipantView;
import com.nefodov.oneline.message.dto.MessageResponse;
import com.nefodov.oneline.message.dto.SendMessageRequest;
import com.nefodov.oneline.stomp.ChatEvent;
import com.nefodov.oneline.stomp.RedisChatBroadcaster;
import com.nefodov.oneline.stomp.TypingRequest;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(OneLineProperties.class)
@ImportRuntimeHints(OneLineConfig.NativeHints.class)
@RegisterReflectionForBinding({
        MessageResponse.class,
        SendMessageRequest.class,
        TypingRequest.class,
        ChatEvent.class,
        ParticipantView.class,
        RedisChatBroadcaster.MessageEnvelope.class,
        RedisChatBroadcaster.EventEnvelope.class
})
public class OneLineConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    static class NativeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerPattern("db/migration/*.sql");
            hints.resources().registerPattern("templates/*.html");
            hints.resources().registerPattern("static/**");
        }
    }
}

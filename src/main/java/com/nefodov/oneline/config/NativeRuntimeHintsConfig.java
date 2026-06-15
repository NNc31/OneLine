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
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(NativeRuntimeHintsConfig.Hints.class)
@RegisterReflectionForBinding({
        MessageResponse.class,
        SendMessageRequest.class,
        TypingRequest.class,
        ChatEvent.class,
        ParticipantView.class,
        RedisChatBroadcaster.MessageEnvelope.class,
        RedisChatBroadcaster.EventEnvelope.class
})
public class NativeRuntimeHintsConfig {

    static class Hints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerPattern("db/migration/*.sql");
            hints.resources().registerPattern("templates/*.html");
            hints.resources().registerPattern("static/**");
        }
    }
}

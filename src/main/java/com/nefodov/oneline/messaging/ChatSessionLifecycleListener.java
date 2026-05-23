package com.nefodov.oneline.messaging;

import com.nefodov.oneline.chat.ChatParticipantService;
import com.nefodov.oneline.chat.ChatSession;
import com.nefodov.oneline.security.MagicLinkAuthentication;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.AbstractSubProtocolEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@AllArgsConstructor
public class ChatSessionLifecycleListener {

    private final ChatParticipantService participantService;
    private final PresenceService presenceService;
    private final ChatBroadcaster broadcaster;
    private final MeterRegistry meterRegistry;

    private final AtomicInteger activeConnections = new AtomicInteger();

    @PostConstruct
    void registerMetrics() {
        meterRegistry.gauge("oneline.ws.connections.active", activeConnections);
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        session(event).ifPresent(s -> {
            activeConnections.incrementAndGet();
            participantService.touch(s.participant());
            presenceService.markOnline(s.chat().getId(), s.participant().getId(), s.participant().getDisplayName());
            broadcastPresence(s.chat().getId());
        });
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        session(event).ifPresent(s -> {
            activeConnections.decrementAndGet();
            presenceService.markOffline(s.chat().getId(), s.participant().getId());
            broadcastPresence(s.chat().getId());
        });
    }

    private void broadcastPresence(Long chatId) {
        broadcaster.broadcastEvent(chatId, ChatEvent.presence(presenceService.online(chatId)));
    }

    private Optional<ChatSession> session(AbstractSubProtocolEvent event) {
        Principal user = event.getUser();
        if (user instanceof MagicLinkAuthentication auth) {
            ChatSession session = auth.session();
            if (session != null && session.participant() != null) {
                return Optional.of(session);
            }
        }
        return Optional.empty();
    }
}

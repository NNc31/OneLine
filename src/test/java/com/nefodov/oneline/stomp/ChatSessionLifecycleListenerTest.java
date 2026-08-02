package com.nefodov.oneline.stomp;

import com.nefodov.oneline.chat.*;
import com.nefodov.oneline.message.dto.MessageResponse;
import com.nefodov.oneline.security.MagicLinkAuthentication;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatSessionLifecycleListenerTest {

    private static final long CHAT_ID = 7L;
    private static final long PARTICIPANT_ID = 3L;

    private ChatParticipantService participantService;
    private PresenceService presenceService;
    private ChatBroadcaster broadcaster;
    private MeterRegistry meterRegistry;
    private ChatSessionLifecycleListener listener;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        participantService = mock(ChatParticipantService.class);
        presenceService = mock(PresenceService.class);
        broadcaster = mock(ChatBroadcaster.class);
        meterRegistry = new SimpleMeterRegistry();
        listener = new ChatSessionLifecycleListener(participantService, presenceService, broadcaster, meterRegistry);
        session = newSession();
    }

    @Test
    @DisplayName("Broadcasts the persisted join notice to the chat")
    void participantJoinedIsBroadcast() {
        MessageResponse notice = new MessageResponse(11L, PARTICIPANT_ID, "Maelle", new byte[0], Instant.parse("2026-01-01T00:00:00Z"), "joined");
        listener.onParticipantJoined(new ParticipantJoinedEvent(CHAT_ID, notice));
        verify(broadcaster).broadcast(CHAT_ID, notice);
    }

    @Test
    @DisplayName("Touches the participant, marks presence and broadcasts it")
    void connectedMarksPresence() {
        when(presenceService.online(CHAT_ID)).thenReturn(List.of());
        listener.onConnected(connectedEvent(new MagicLinkAuthentication(session)));
        verify(participantService).touch(session.participant());
        verify(presenceService).markOnline(CHAT_ID, PARTICIPANT_ID, "Maelle");
        verify(broadcaster).broadcastEvent(eq(CHAT_ID), any(ChatEvent.class));
    }

    @Test
    @DisplayName("Clears presence and broadcasts it")
    void disconnectClearsPresence() {
        when(presenceService.online(CHAT_ID)).thenReturn(List.of());
        listener.onDisconnect(disconnectEvent(new MagicLinkAuthentication(session)));
        verify(presenceService).markOffline(CHAT_ID, PARTICIPANT_ID);
        verify(broadcaster).broadcastEvent(eq(CHAT_ID), any(ChatEvent.class));
    }

    @Test
    @DisplayName("Events without a magic link principal are ignored")
    void foreignPrincipalIsIgnored() {
        Principal foreign = mock(Principal.class);
        listener.onConnected(connectedEvent(foreign));
        listener.onDisconnect(disconnectEvent(foreign));
        verify(presenceService, never()).markOnline(anyLong(), anyLong(), any());
        verify(presenceService, never()).markOffline(anyLong(), anyLong());
        verify(broadcaster, never()).broadcastEvent(anyLong(), any());
    }

    @Test
    @DisplayName("Active connection indicator tracks connects and disconnects")
    void connectionGaugeTracksSessions() {
        listener.registerMetrics();
        MagicLinkAuthentication auth = new MagicLinkAuthentication(session);
        when(presenceService.online(CHAT_ID)).thenReturn(List.of());
        listener.onConnected(connectedEvent(auth));
        assertEquals(1.0, meterRegistry.get("oneline.ws.connections.active").gauge().value());
        listener.onDisconnect(disconnectEvent(auth));
        assertEquals(0.0, meterRegistry.get("oneline.ws.connections.active").gauge().value());
    }

    private static SessionConnectedEvent connectedEvent(Principal user) {
        SessionConnectedEvent event = mock(SessionConnectedEvent.class);
        when(event.getUser()).thenReturn(user);
        return event;
    }

    private static SessionDisconnectEvent disconnectEvent(Principal user) {
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getUser()).thenReturn(user);
        return event;
    }

    private static ChatSession newSession() {
        Chat chat = new Chat();
        chat.setId(CHAT_ID);
        chat.setPublicId(UUID.randomUUID());
        ChatParticipant participant = new ChatParticipant();
        participant.setId(PARTICIPANT_ID);
        participant.setChat(chat);
        participant.setDisplayName("Maelle");
        return new ChatSession(chat, participant);
    }
}

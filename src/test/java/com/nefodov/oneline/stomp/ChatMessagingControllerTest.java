package com.nefodov.oneline.stomp;

import com.nefodov.oneline.chat.*;
import com.nefodov.oneline.message.Message;
import com.nefodov.oneline.message.MessageService;
import com.nefodov.oneline.message.dto.MessageResponse;
import com.nefodov.oneline.message.dto.SendMessageRequest;
import com.nefodov.oneline.ratelimit.RateLimiter;
import com.nefodov.oneline.security.MagicLinkAuthentication;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessagingException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatMessagingControllerTest {

    private static final long CHAT_ID = 7L;

    private MessageService messageService;
    private ChatParticipantService participantService;
    private ChatBroadcaster broadcaster;
    private PresenceService presenceService;
    private RateLimiter rateLimiter;
    private ChatMessagingController controller;
    private MagicLinkAuthentication auth;

    @BeforeEach
    void setUp() {
        messageService = mock(MessageService.class);
        participantService = mock(ChatParticipantService.class);
        broadcaster = mock(ChatBroadcaster.class);
        presenceService = mock(PresenceService.class);
        rateLimiter = mock(RateLimiter.class);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        controller = new ChatMessagingController(messageService, participantService, broadcaster, presenceService, rateLimiter, meterRegistry);
        auth = new MagicLinkAuthentication(newSession());
    }

    @Test
    @DisplayName("send persists, touches the participant and broadcasts the stored message")
    void sendSuccess() {
        when(rateLimiter.tryAcquire(eq("message"), anyString())).thenReturn(true);
        byte[] content = {1, 2, 3};
        UUID clientId = UUID.randomUUID();
        Message stored = mockStoredMessage(content);
        when(messageService.send(any(), eq(clientId), eq(content))).thenReturn(stored);

        controller.send(CHAT_ID, new SendMessageRequest(clientId, content), auth);

        verify(participantService).touch(any());
        verify(broadcaster).broadcast(eq(CHAT_ID), any(MessageResponse.class));
    }

    @Test
    @DisplayName("send rejects a chat id that does not match the session")
    void sendRejectsChatMismatch() {
        var request = new SendMessageRequest(UUID.randomUUID(), new byte[]{1});
        assertThrows(MessagingException.class, () -> controller.send(999L, request, auth));
        verify(messageService, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("send rejects when the rate limiter is exhausted")
    void sendRejectsWhenRateLimited() {
        when(rateLimiter.tryAcquire(eq("message"), anyString())).thenReturn(false);
        var request = new SendMessageRequest(UUID.randomUUID(), new byte[]{1});
        assertThrows(MessagingException.class, () -> controller.send(CHAT_ID, request, auth));
        verify(messageService, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("typing broadcasts a typing event")
    void typingBroadcasts() {
        controller.typing(CHAT_ID, new TypingRequest(true), auth);
        verify(broadcaster).broadcastEvent(eq(CHAT_ID), any(ChatEvent.class));
    }

    @Test
    @DisplayName("typing rejects a chat id mismatch")
    void typingRejectsChatMismatch() {
        TypingRequest request = new TypingRequest(true);
        assertThrows(MessagingException.class, () -> controller.typing(999L, request, auth));
        verify(broadcaster, never()).broadcastEvent(anyLong(), any());
    }

    @Test
    @DisplayName("heartbeat marks online and broadcasts presence only when something was evicted")
    void heartbeatBroadcastsOnEviction() {
        when(presenceService.evictStale(CHAT_ID)).thenReturn(1);
        when(presenceService.online(CHAT_ID)).thenReturn(List.of());

        controller.heartbeat(CHAT_ID, auth);

        verify(presenceService).markOnline(eq(CHAT_ID), anyLong(), anyString());
        verify(broadcaster).broadcastEvent(eq(CHAT_ID), any(ChatEvent.class));
    }

    @Test
    @DisplayName("heartbeat stays quiet when nothing was evicted")
    void heartbeatQuietWithoutEviction() {
        when(presenceService.evictStale(CHAT_ID)).thenReturn(0);

        controller.heartbeat(CHAT_ID, auth);

        verify(presenceService).markOnline(eq(CHAT_ID), anyLong(), anyString());
        verify(broadcaster, never()).broadcastEvent(anyLong(), any());
    }

    @Test
    @DisplayName("heartbeat rejects a chat id mismatch")
    void heartbeatRejectsChatMismatch() {
        assertThrows(MessagingException.class, () -> controller.heartbeat(999L, auth));
        verify(presenceService, never()).markOnline(anyLong(), anyLong(), anyString());
    }

    private Message mockStoredMessage(byte[] content) {
        Message message = mock(Message.class);
        ChatParticipant participant = new ChatParticipant();
        participant.setId(3L);
        participant.setDisplayName("Tester");
        when(message.getId()).thenReturn(11L);
        when(message.getParticipant()).thenReturn(participant);
        when(message.getContent()).thenReturn(content);
        when(message.getCreatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        return message;
    }

    private static ChatSession newSession() {
        Chat chat = new Chat();
        chat.setId(CHAT_ID);
        chat.setPublicId(UUID.randomUUID());
        ChatParticipant participant = new ChatParticipant();
        participant.setId(3L);
        participant.setChat(chat);
        participant.setDisplayName("Tester");
        return new ChatSession(chat, participant);
    }
}

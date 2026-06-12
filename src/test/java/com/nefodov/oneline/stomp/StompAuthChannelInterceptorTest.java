package com.nefodov.oneline.stomp;

import com.nefodov.oneline.chat.Chat;
import com.nefodov.oneline.chat.ChatParticipant;
import com.nefodov.oneline.chat.ChatParticipantService;
import com.nefodov.oneline.chat.ChatSession;
import com.nefodov.oneline.security.MagicLinkAuthentication;
import com.nefodov.oneline.security.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StompAuthChannelInterceptorTest {

    private static final long CHAT_ID = 7L;
    private static final byte[] CHAT_HASH = {1, 2, 3, 4};

    private ChatParticipantService participantService;
    private TokenHasher tokenHasher;
    private StompAuthChannelInterceptor interceptor;
    private final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        participantService = mock(ChatParticipantService.class);
        tokenHasher = mock(TokenHasher.class);
        interceptor = new StompAuthChannelInterceptor(participantService, tokenHasher);
    }

    @Test
    @DisplayName("A message without a STOMP accessor passes through untouched")
    void passesThroughNonStompMessage() {
        Message<byte[]> plain = MessageBuilder.withPayload(new byte[0]).build();
        assertSame(plain, interceptor.preSend(plain, channel));
        verify(participantService, never()).resolveBySession(any());
    }

    @Test
    @DisplayName("A non-CONNECT, non-SUBSCRIBE command passes through")
    void passesThroughOtherCommands() {
        Message<byte[]> message = stompMessage(StompCommand.SEND, accessor -> {});
        assertSame(message, interceptor.preSend(message, channel));
        verify(participantService, never()).resolveBySession(any());
    }

    @Test
    @DisplayName("CONNECT with matching session and chat token sets the authenticated user")
    void connectAuthenticatesSuccessfully() {
        ChatParticipant participant = participantWithChatHash(CHAT_HASH);
        when(tokenHasher.hash("chat-token")).thenReturn(CHAT_HASH);
        when(participantService.resolveBySession("sess")).thenReturn(Optional.of(participant));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(Map.of(SessionCookieHandshakeInterceptor.SESSION_TOKEN_ATTRIBUTE, "sess"));
        accessor.setNativeHeader(StompAuthChannelInterceptor.CHAT_TOKEN_HEADER, "chat-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, channel);
        assertInstanceOf(MagicLinkAuthentication.class, accessor.getUser());
    }

    @Test
    @DisplayName("CONNECT without a session token is rejected")
    void connectRejectsMissingTokens() {
        Message<byte[]> message = stompMessage(StompCommand.CONNECT, accessor -> accessor.setNativeHeader(StompAuthChannelInterceptor.CHAT_TOKEN_HEADER, "chat-token"));
        assertThrows(MessagingException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    @DisplayName("CONNECT with a chat token that matches no participant is rejected")
    void connectRejectsUnknownParticipant() {
        when(tokenHasher.hash("chat-token")).thenReturn(CHAT_HASH);
        when(participantService.resolveBySession("sess")).thenReturn(Optional.empty());
        Message<byte[]> message = stompMessage(StompCommand.CONNECT, accessor -> {
            accessor.setSessionAttributes(Map.of(SessionCookieHandshakeInterceptor.SESSION_TOKEN_ATTRIBUTE, "sess"));
            accessor.setNativeHeader(StompAuthChannelInterceptor.CHAT_TOKEN_HEADER, "chat-token");
        });
        assertThrows(MessagingException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    @DisplayName("CONNECT where the participant's chat hash differs is rejected")
    void connectRejectsChatHashMismatch() {
        ChatParticipant participant = participantWithChatHash(new byte[]{9, 9, 9, 9});
        when(tokenHasher.hash("chat-token")).thenReturn(CHAT_HASH);
        when(participantService.resolveBySession("sess")).thenReturn(Optional.of(participant));
        Message<byte[]> message = stompMessage(StompCommand.CONNECT, accessor -> {
            accessor.setSessionAttributes(Map.of(SessionCookieHandshakeInterceptor.SESSION_TOKEN_ATTRIBUTE, "sess"));
            accessor.setNativeHeader(StompAuthChannelInterceptor.CHAT_TOKEN_HEADER, "chat-token");
        });
        assertThrows(MessagingException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    @DisplayName("SUBSCRIBE to the chat's own message topic is allowed")
    void subscribeToBaseTopicAllowed() {
        Message<byte[]> message = subscribeMessage(authenticated(), "/topic/chat." + CHAT_ID);
        assertSame(message, interceptor.preSend(message, channel));
    }

    @Test
    @DisplayName("SUBSCRIBE to the chat's events topic is allowed")
    void subscribeToEventsTopicAllowed() {
        Message<byte[]> message = subscribeMessage(authenticated(), "/topic/chat." + CHAT_ID + ".events");
        assertSame(message, interceptor.preSend(message, channel));
    }

    @Test
    @DisplayName("SUBSCRIBE to the shared system events topic is allowed")
    void subscribeToSystemTopicAllowed() {
        Message<byte[]> message = subscribeMessage(authenticated(), GracefulShutdownBroadcaster.SYSTEM_EVENTS_TOPIC);
        assertSame(message, interceptor.preSend(message, channel));
    }

    @Test
    @DisplayName("SUBSCRIBE to another chat's topic is forbidden")
    void subscribeToForeignTopicForbidden() {
        Message<byte[]> message = subscribeMessage(authenticated(), "/topic/chat.999");
        assertThrows(MessagingException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    @DisplayName("SUBSCRIBE without an authenticated principal is rejected")
    void subscribeWithoutPrincipalRejected() {
        Message<byte[]> message = subscribeMessage(null, "/topic/chat." + CHAT_ID);
        assertThrows(MessagingException.class, () -> interceptor.preSend(message, channel));
    }

    private MagicLinkAuthentication authenticated() {
        Chat chat = new Chat();
        chat.setId(CHAT_ID);
        ChatParticipant participant = new ChatParticipant();
        participant.setChat(chat);
        return new MagicLinkAuthentication(new ChatSession(chat, participant));
    }

    private ChatParticipant participantWithChatHash(byte[] chatHash) {
        Chat chat = new Chat();
        chat.setId(CHAT_ID);
        chat.setChatTokenHash(chatHash);
        ChatParticipant participant = new ChatParticipant();
        participant.setChat(chat);
        return participant;
    }

    private static Message<byte[]> subscribeMessage(MagicLinkAuthentication user, String destination) {
        return stompMessage(StompCommand.SUBSCRIBE, accessor -> {
            if (user != null) {
                accessor.setUser(user);
            }
            accessor.setDestination(destination);
        });
    }

    private static Message<byte[]> stompMessage(StompCommand command, java.util.function.Consumer<StompHeaderAccessor> customizer) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        customizer.accept(accessor);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}

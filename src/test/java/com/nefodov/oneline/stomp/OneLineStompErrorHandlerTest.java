package com.nefodov.oneline.stomp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OneLineStompErrorHandlerTest {

    private final OneLineStompErrorHandler handler = new OneLineStompErrorHandler();

    private final Message<byte[]> clientMessage = Mockito.mock(Message.class);

    @Test
    @DisplayName("Sets exception message into a STOMP ERROR frame body")
    void wrapsMessageIntoErrorFrame() {
        Message<byte[]> result = handler.handleClientMessageProcessingError(clientMessage, new RuntimeException("bad chat token"));
        assertEquals("{\"error\":\"bad chat token\"}", body(result));
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertNotNull(accessor);
        assertEquals("bad chat token", accessor.getMessage());
    }

    @Test
    @DisplayName("Falls back to 'Internal error' when the root cause has no message")
    void fallsBackWhenMessageMissing() {
        Message<byte[]> result = handler.handleClientMessageProcessingError(clientMessage, new RuntimeException());
        assertEquals("{\"error\":\"Internal error\"}", body(result));
    }

    @Test
    @DisplayName("Falls back to 'Internal error' on a blank message")
    void fallsBackOnBlankMessage() {
        Message<byte[]> result = handler.handleClientMessageProcessingError(clientMessage, new RuntimeException("   "));
        assertEquals("{\"error\":\"Internal error\"}", body(result));
    }

    @Test
    @DisplayName("Unwraps nested causes down to the root message")
    void unwrapsNestedCauses() {
        RuntimeException root = new RuntimeException("root cause");
        RuntimeException middle = new RuntimeException("middle", root);
        RuntimeException outer = new RuntimeException("outer", middle);
        Message<byte[]> result = handler.handleClientMessageProcessingError(clientMessage, outer);
        assertEquals("{\"error\":\"root cause\"}", body(result));
    }

    @Test
    @DisplayName("Escapes quotes and backslashes in the error message")
    void escapesQuotesAndBackslashes() {
        Message<byte[]> result = handler.handleClientMessageProcessingError(clientMessage, new RuntimeException("bad \"path\\file\""));
        assertEquals("{\"error\":\"bad \\\"path\\\\file\\\"\"}", body(result));
    }

    private static String body(Message<byte[]> message) {
        return new String(message.getPayload(), StandardCharsets.UTF_8);
    }
}

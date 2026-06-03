package com.nefodov.oneline.stomp;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;

@Component
public class OneLineStompErrorHandler extends StompSubProtocolErrorHandler {

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        String reason = extractReason(ex);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(reason);
        accessor.setLeaveMutable(true);
        String escapedReason = reason.replace("\\", "\\\\").replace("\"", "\\\"");
        byte[] body = ("{\"error\":\"" + escapedReason + "\"}").getBytes(StandardCharsets.UTF_8);
        return MessageBuilder.createMessage(body, accessor.getMessageHeaders());
    }

    private static String extractReason(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? "Internal error" : message;
    }
}

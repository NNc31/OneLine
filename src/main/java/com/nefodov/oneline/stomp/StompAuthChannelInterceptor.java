package com.nefodov.oneline.stomp;

import com.nefodov.oneline.chat.ChatParticipant;
import com.nefodov.oneline.chat.ChatParticipantService;
import com.nefodov.oneline.chat.ChatSession;
import com.nefodov.oneline.security.MagicLinkAuthentication;
import com.nefodov.oneline.security.TokenHasher;
import lombok.AllArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.Principal;
import java.util.Map;

@Component
@AllArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    static final String CHAT_TOKEN_HEADER = "X-Chat-Token";

    private final ChatParticipantService participantService;
    private final TokenHasher tokenHasher;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            default -> {
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        String sessionToken = attributes == null ? null : (String) attributes.get(SessionCookieHandshakeInterceptor.SESSION_TOKEN_ATTRIBUTE);
        String chatToken = accessor.getFirstNativeHeader(CHAT_TOKEN_HEADER);
        if (sessionToken == null || chatToken == null) {
            throw new MessagingException("Missing session or chat token");
        }
        byte[] chatHash = tokenHasher.hash(chatToken);
        ChatParticipant participant = participantService.resolveBySession(sessionToken)
                .filter(p -> MessageDigest.isEqual(p.getChat().getChatTokenHash(), chatHash))
                .orElseThrow(() -> new MessagingException("Unauthorized"));
        accessor.setUser(new MagicLinkAuthentication(new ChatSession(participant.getChat(), participant)));
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (!(principal instanceof MagicLinkAuthentication auth)) {
            throw new MessagingException("Unauthorized");
        }
        String destination = accessor.getDestination();
        String base = ChatBroadcaster.TOPIC_PREFIX + auth.session().chat().getId();
        if (!base.equals(destination) && !(base + ChatBroadcaster.EVENTS_SUFFIX).equals(destination)) {
            throw new MessagingException("Forbidden subscription");
        }
    }
}

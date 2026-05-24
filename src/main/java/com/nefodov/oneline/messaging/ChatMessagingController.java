package com.nefodov.oneline.messaging;

import com.nefodov.oneline.chat.ChatParticipantService;
import com.nefodov.oneline.chat.ChatSession;
import com.nefodov.oneline.chat.dto.ParticipantView;
import com.nefodov.oneline.message.Message;
import com.nefodov.oneline.message.MessageService;
import com.nefodov.oneline.message.dto.MessageResponse;
import com.nefodov.oneline.message.dto.SendMessageRequest;
import com.nefodov.oneline.security.MagicLinkAuthentication;
import com.nefodov.oneline.support.RateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@AllArgsConstructor
public class ChatMessagingController {

    private static final String BUCKET_MESSAGE = "message";

    private final MessageService messageService;
    private final ChatParticipantService participantService;
    private final ChatBroadcaster broadcaster;
    private final PresenceService presenceService;
    private final RateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;

    @MessageMapping("/chat.{chatId}.send")
    public void send(@DestinationVariable Long chatId, @Valid @Payload SendMessageRequest request, MagicLinkAuthentication auth) {
        ChatSession session = auth.session();
        if (!session.chat().getId().equals(chatId)) {
            throw new MessagingException("Chat mismatch");
        }
        if (!rateLimiter.tryAcquire(BUCKET_MESSAGE, String.valueOf(session.participant().getId()))) {
            meterRegistry.counter("oneline.ratelimit.rejected", "bucket", BUCKET_MESSAGE).increment();
            throw new MessagingException("You're sending messages too fast");
        }
        Message stored = messageService.send(session, request.clientMessageId(), request.content());
        participantService.touch(session.participant());
        broadcaster.broadcast(chatId, new MessageResponse(
                stored.getId(),
                stored.getParticipant().getId(),
                stored.getParticipant().getDisplayName(),
                stored.getContent(),
                stored.getCreatedAt()
        ));
        meterRegistry.counter("oneline.messages.sent").increment();
    }

    @MessageMapping("/chat.{chatId}.typing")
    public void typing(@DestinationVariable Long chatId, @Payload TypingRequest request, MagicLinkAuthentication auth) {
        ChatSession session = auth.session();
        if (!session.chat().getId().equals(chatId)) {
            throw new MessagingException("Chat mismatch");
        }
        ParticipantView me = new ParticipantView(session.participant().getId(), session.participant().getDisplayName());
        broadcaster.broadcastEvent(chatId, ChatEvent.typing(me, request.typing()));
    }

    @MessageMapping("/chat.{chatId}.heartbeat")
    public void heartbeat(@DestinationVariable Long chatId, MagicLinkAuthentication auth) {
        ChatSession session = auth.session();
        if (!session.chat().getId().equals(chatId)) {
            throw new MessagingException("Chat mismatch");
        }
        presenceService.markOnline(chatId, session.participant().getId(), session.participant().getDisplayName());
        if (presenceService.evictStale(chatId) > 0) {
            broadcaster.broadcastEvent(chatId, ChatEvent.presence(presenceService.online(chatId)));
        }
    }
}

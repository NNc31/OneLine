package com.nefodov.oneline.messaging;

import com.nefodov.oneline.chat.ChatParticipantService;
import com.nefodov.oneline.chat.ChatSession;
import com.nefodov.oneline.message.Message;
import com.nefodov.oneline.message.MessageService;
import com.nefodov.oneline.message.dto.MessageResponse;
import com.nefodov.oneline.message.dto.SendMessageRequest;
import com.nefodov.oneline.security.MagicLinkAuthentication;
import com.nefodov.oneline.support.RateLimiter;
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
    private final RateLimiter rateLimiter;

    @MessageMapping("/chat.{chatId}.send")
    public void send(@DestinationVariable Long chatId, @Valid @Payload SendMessageRequest request, MagicLinkAuthentication auth) {
        ChatSession session = auth.session();
        if (!session.chat().getId().equals(chatId)) {
            throw new MessagingException("Chat mismatch");
        }
        if (!rateLimiter.tryAcquire(BUCKET_MESSAGE, String.valueOf(session.participant().getId()))) {
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
    }
}

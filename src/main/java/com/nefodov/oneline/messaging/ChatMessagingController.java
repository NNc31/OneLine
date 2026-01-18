package com.nefodov.oneline.messaging;

import com.nefodov.oneline.chat.ChatParticipantService;
import com.nefodov.oneline.chat.ChatSession;
import com.nefodov.oneline.message.MessageService;
import com.nefodov.oneline.message.dto.MessageResponse;
import com.nefodov.oneline.message.dto.SendMessageRequest;
import com.nefodov.oneline.security.MagicLinkAuthentication;
import lombok.AllArgsConstructor;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@AllArgsConstructor
public class ChatMessagingController {

    private final MessageService messageService;
    private final ChatParticipantService participantService;
    private final ChatBroadcaster broadcaster;

    @MessageMapping("/chat.{chatId}.send")
    public void send(@DestinationVariable Long chatId, @Payload SendMessageRequest request, MagicLinkAuthentication auth) {
        ChatSession session = auth.session();
        if (!session.chat().getId().equals(chatId)) {
            throw new MessagingException("Chat mismatch");
        }
        MessageService.StoredMessage stored = messageService.send(session, request.clientMessageId(), request.content());
        participantService.touch(session.participant());
        broadcaster.broadcast(chatId, new MessageResponse(
                stored.message().getId(),
                stored.message().getParticipant().getId(),
                stored.message().getParticipant().getDisplayName(),
                stored.plaintext(),
                stored.message().getCreatedAt()
        ));
    }
}

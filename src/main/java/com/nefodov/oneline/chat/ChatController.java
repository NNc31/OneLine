package com.nefodov.oneline.chat;

import com.nefodov.oneline.chat.dto.*;
import com.nefodov.oneline.message.MessageService;
import com.nefodov.oneline.message.dto.MessageResponse;
import com.nefodov.oneline.message.dto.SendMessageRequest;
import com.nefodov.oneline.messaging.ChatBroadcaster;
import com.nefodov.oneline.support.ClientIpResolver;
import com.nefodov.oneline.support.RateLimiter;
import com.nefodov.oneline.support.SessionCookieFactory;
import com.nefodov.oneline.support.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chats")
@AllArgsConstructor
public class ChatController {

    private static final String BUCKET_CREATE_CHAT = "create-chat";
    private static final String BUCKET_JOIN = "join";

    private final ChatService chatService;
    private final ChatParticipantService participantService;
    private final ChatBroadcaster broadcaster;
    private final MessageService messageService;
    private final SessionCookieFactory sessionCookieFactory;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    public CreateChatResponse create(@RequestBody(required = false) CreateChatRequest request, HttpServletRequest httpRequest) {
        enforceRateLimit(BUCKET_CREATE_CHAT, httpRequest);
        String name = request == null ? null : trimOrNull(request.name());
        ChatService.CreatedChat created = chatService.create(name);
        return new CreateChatResponse(created.chatToken(), created.chat().getName());
    }

    @GetMapping("/{chatToken}")
    public ChatMetaResponse meta(@PathVariable String chatToken, @AuthenticationPrincipal ChatSession session) {
        Chat chat = chatService.findActive(chatToken);
        ParticipantView me = session == null ? null : new ParticipantView(session.participant().getId(), session.participant().getDisplayName());
        return new ChatMetaResponse(chat.getId(), chat.getName(), participantService.countParticipants(chat), me);
    }

    @PostMapping("/{chatToken}/join")
    public ResponseEntity<JoinChatResponse> join(@PathVariable String chatToken, @RequestBody JoinChatRequest request, HttpServletRequest httpRequest) {
        enforceRateLimit(BUCKET_JOIN, httpRequest);
        Chat chat = chatService.findActive(chatToken);
        ChatParticipantService.JoinedParticipant joined = participantService.join(chat, request.displayName());
        ResponseCookie cookie = sessionCookieFactory.build(joined.sessionToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new JoinChatResponse(chat.getId(), new ParticipantView(joined.participant().getId(), joined.participant().getDisplayName())));
    }

    @GetMapping("/{chatToken}/messages")
    public List<MessageResponse> history(
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal ChatSession session) {
        return messageService.history(session, before, limit).stream().map(this::toResponse).toList();
    }

    @PostMapping("/{chatToken}/messages")
    public MessageResponse send(@RequestBody SendMessageRequest request, @AuthenticationPrincipal ChatSession session) {
        MessageService.StoredMessage stored = messageService.send(session, request.clientMessageId(), request.content());
        participantService.touch(session.participant());
        MessageResponse payload = toResponse(stored);
        broadcaster.broadcast(session.chat().getId(), payload);
        return payload;
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private MessageResponse toResponse(MessageService.StoredMessage stored) {
        return new MessageResponse(
                stored.message().getId(),
                stored.message().getParticipant().getId(),
                stored.message().getParticipant().getDisplayName(),
                stored.plaintext(),
                stored.message().getCreatedAt()
        );
    }

    private void enforceRateLimit(String bucket, HttpServletRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        if (!rateLimiter.tryAcquire(bucket, clientIp)) {
            throw new TooManyRequestsException("Too many requests for " + bucket);
        }
    }
}

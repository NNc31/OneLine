package com.nefodov.oneline.chat;

import com.nefodov.oneline.chat.dto.*;
import com.nefodov.oneline.message.Message;
import com.nefodov.oneline.message.MessageService;
import com.nefodov.oneline.message.dto.MessageResponse;
import com.nefodov.oneline.web.exception.NotFoundException;
import com.nefodov.oneline.ratelimit.RateLimiter;
import com.nefodov.oneline.security.SessionCookieFactory;
import com.nefodov.oneline.web.exception.TooManyRequestsException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chats")
@AllArgsConstructor
public class ChatApiController {

    private static final String CHAT_TOKEN_HEADER = "X-Chat-Token";
    private static final String BUCKET_CREATE_CHAT = "create-chat";
    private static final String BUCKET_JOIN = "join";

    private final ChatService chatService;
    private final ChatParticipantService participantService;
    private final MessageService messageService;
    private final SessionCookieFactory sessionCookieFactory;
    private final RateLimiter rateLimiter;
    private final PresenceService presenceService;
    private final MeterRegistry meterRegistry;

    @GetMapping("/{publicId}")
    public ChatMetaResponse meta(@PathVariable("publicId") UUID publicId,
                                 @RequestHeader(value = CHAT_TOKEN_HEADER, required = false) String chatToken,
                                 @AuthenticationPrincipal ChatSession session) {
        Chat chat = resolveChat(publicId, chatToken);
        ParticipantView me = session == null ? null : new ParticipantView(session.participant().getId(), session.participant().getDisplayName());
        return new ChatMetaResponse(chat.getId(), participantService.countParticipants(chat), me, chat.getMessageTtlSeconds());
    }

    @PostMapping("/{publicId}/join")
    public ResponseEntity<JoinChatResponse> join(@PathVariable("publicId") UUID publicId,
                                                 @RequestHeader(CHAT_TOKEN_HEADER) String chatToken,
                                                 @AuthenticationPrincipal ChatSession session,
                                                 @Valid @RequestBody JoinChatRequest request,
                                                 HttpServletRequest httpRequest) {
        Chat chat = chatService.findActive(publicId, chatToken);
        if (session != null && session.chat().getId().equals(chat.getId())) {
            participantService.touch(session.participant());
            return ResponseEntity.ok().body(new JoinChatResponse(chat.getId(), new ParticipantView(session.participant().getId(), session.participant().getDisplayName())));
        }

        enforceRateLimit(BUCKET_JOIN, httpRequest);

        ChatParticipantService.JoinedParticipant joined = participantService.join(chat, request.displayName());
        ResponseCookie cookie = sessionCookieFactory.build(joined.sessionToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new JoinChatResponse(chat.getId(), new ParticipantView(joined.participant().getId(), joined.participant().getDisplayName())));
    }

    @GetMapping("/{publicId}/presence")
    public List<ParticipantView> presence(@AuthenticationPrincipal ChatSession session) {
        return presenceService.online(session.chat().getId());
    }

    @GetMapping("/{publicId}/messages")
    public List<MessageResponse> history(@RequestParam(required = false) Long before,
                                         @RequestParam(required = false) Integer limit,
                                         @AuthenticationPrincipal ChatSession session) {
        return messageService.history(session, before, limit).stream().map(this::toResponse).toList();
    }

    @PostMapping
    public CreateChatResponse create(@Valid @RequestBody CreateChatRequest request, HttpServletRequest httpRequest) {
        enforceRateLimit(BUCKET_CREATE_CHAT, httpRequest);
        Chat chat = chatService.create(request.authToken(), request.messageTtlSeconds());
        return new CreateChatResponse(chat.getPublicId());
    }

    private Chat resolveChat(UUID publicId, String chatToken) {
        if (chatToken == null || chatToken.isBlank()) {
            return chatService.findByPublicId(publicId);
        }
        try {
            return chatService.findActive(publicId, chatToken);
        } catch (NotFoundException e) {
            return chatService.findByPublicId(publicId);
        }
    }

    private MessageResponse toResponse(Message stored) {
        return new MessageResponse(
                stored.getId(),
                stored.getParticipant().getId(),
                stored.getParticipant().getDisplayName(),
                stored.getContent(),
                stored.getCreatedAt()
        );
    }

    private void enforceRateLimit(String bucket, HttpServletRequest request) {
        if (!rateLimiter.tryAcquire(bucket, request.getRemoteAddr())) {
            meterRegistry.counter("oneline.ratelimit.rejected", "bucket", bucket).increment();
            throw new TooManyRequestsException("Too many requests for " + bucket);
        }
    }
}

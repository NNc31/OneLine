package com.nefodov.oneline.web;

import com.nefodov.oneline.chat.Chat;
import com.nefodov.oneline.chat.ChatParticipantService;
import com.nefodov.oneline.chat.ChatService;
import com.nefodov.oneline.chat.ChatSession;
import com.nefodov.oneline.message.MessageService;
import com.nefodov.oneline.message.dto.MessageResponse;
import com.nefodov.oneline.support.ClientIpResolver;
import com.nefodov.oneline.support.RateLimiter;
import com.nefodov.oneline.support.SessionCookieFactory;
import com.nefodov.oneline.support.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@AllArgsConstructor
public class ViewController {

    private static final String BUCKET_CREATE_CHAT = "create-chat";
    private static final String BUCKET_JOIN = "join";

    private final ChatService chatService;
    private final ChatParticipantService participantService;
    private final MessageService messageService;
    private final SessionCookieFactory sessionCookieFactory;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/new")
    public String createChat(HttpServletRequest httpRequest) {
        enforceRateLimit(BUCKET_CREATE_CHAT, httpRequest);
        ChatService.CreatedChat created = chatService.create(null);
        return "redirect:/c/" + created.chatToken();
    }

    @GetMapping("/c/{chatToken}")
    public String chatPage(@PathVariable String chatToken, @AuthenticationPrincipal ChatSession session, Model model) {
        Chat chat = chatService.findActive(chatToken);
        model.addAttribute("chatToken", chatToken);
        model.addAttribute("chatName", chat.getName());
        if (session == null) {
            model.addAttribute("authenticated", false);
            return "chat";
        }
        List<MessageResponse> history = messageService.history(session, null, null).stream()
                .map(stored -> new MessageResponse(
                        stored.message().getId(),
                        stored.message().getParticipant().getId(),
                        stored.message().getParticipant().getDisplayName(),
                        stored.plaintext(), stored.message().getCreatedAt()
                )).toList().reversed();
        model.addAttribute("authenticated", true);
        model.addAttribute("chatId", chat.getId());
        model.addAttribute("me", session.participant());
        model.addAttribute("history", history);
        return "chat";
    }

    @PostMapping("/c/{chatToken}/join")
    public String joinChat(@PathVariable String chatToken, @RequestParam("displayName") String displayName,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        enforceRateLimit(BUCKET_JOIN, httpRequest);
        Chat chat = chatService.findActive(chatToken);
        ChatParticipantService.JoinedParticipant joined = participantService.join(chat, displayName);
        ResponseCookie cookie = sessionCookieFactory.build(joined.sessionToken());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return "redirect:/c/" + chatToken;
    }

    private void enforceRateLimit(String bucket, HttpServletRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        if (!rateLimiter.tryAcquire(bucket, clientIp)) {
            throw new TooManyRequestsException("Too many requests for " + bucket);
        }
    }

}

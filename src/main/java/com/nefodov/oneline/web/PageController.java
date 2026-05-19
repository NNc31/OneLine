package com.nefodov.oneline.web;

import com.nefodov.oneline.chat.ChatService;
import com.nefodov.oneline.support.ClientIpResolver;
import com.nefodov.oneline.support.RateLimiter;
import com.nefodov.oneline.support.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@Controller
@AllArgsConstructor
public class PageController {

    private static final String BUCKET_CREATE_CHAT = "create-chat";

    private final ChatService chatService;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/me")
    public String me() {
        return "me";
    }

    @PostMapping("/new")
    public String createChat(HttpServletRequest httpRequest) {
        enforceRateLimit(BUCKET_CREATE_CHAT, httpRequest);
        ChatService.CreatedChat created = chatService.create();
        return "redirect:/c/" + created.chat().getPublicId() + "#" + created.chatToken();
    }

    @GetMapping("/c/{publicId}")
    public String chatPage(@PathVariable UUID publicId, Model model) {
        chatService.findByPublicId(publicId);
        model.addAttribute("publicId", publicId);
        return "chat";
    }

    private void enforceRateLimit(String bucket, HttpServletRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        if (!rateLimiter.tryAcquire(bucket, clientIp)) {
            throw new TooManyRequestsException("Too many requests for " + bucket);
        }
    }
}

package com.nefodov.oneline.web;

import com.nefodov.oneline.chat.ChatService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@Controller
@AllArgsConstructor
public class PageController {

    private final ChatService chatService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/me")
    public String me() {
        return "me";
    }

    @GetMapping("/c/{publicId}")
    public String chatPage(@PathVariable UUID publicId, Model model) {
        chatService.findByPublicId(publicId);
        model.addAttribute("publicId", publicId);
        return "chat";
    }
}

package com.nefodov.oneline.security;

import com.nefodov.oneline.chat.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@AllArgsConstructor
public class MagicLinkAuthenticationFilter extends OncePerRequestFilter {

    private static final String CHAT_TOKEN_HEADER = "X-Chat-Token";
    private static final String SESSION_TOKEN_HEADER = "X-Session-Token";
    private static final Pattern CHAT_PATH = Pattern.compile("^(?:/api/chats|/c)/(?<publicId>[0-9a-fA-F-]{36})(?:/.*)?$");

    private final ChatService chatService;
    private final ChatParticipantService participantService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String sessionToken = readSessionHeader(request);
        authenticate(request, sessionToken).ifPresent(auth -> SecurityContextHolder.getContext().setAuthentication(auth));
        chain.doFilter(request, response);
    }

    private Optional<MagicLinkAuthentication> authenticate(HttpServletRequest request, String sessionToken) {
        UUID publicId = extractPublicId(request);
        if (publicId == null) {
            return Optional.empty();
        }
        String chatToken = request.getHeader(CHAT_TOKEN_HEADER);
        if (!StringUtils.hasText(chatToken)) {
            return Optional.empty();
        }
        if (sessionToken == null) {
            return Optional.empty();
        }
        Chat chat;
        try {
            chat = chatService.findActive(publicId, chatToken);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        return participantService.resolveBySession(sessionToken)
                .filter(participant -> belongsToChat(participant, chat))
                .map(participant -> new MagicLinkAuthentication(new ChatSession(chat, participant)));
    }

    private UUID extractPublicId(HttpServletRequest request) {
        Matcher matcher = CHAT_PATH.matcher(request.getRequestURI());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return UUID.fromString(matcher.group("publicId"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String readSessionHeader(HttpServletRequest request) {
        String sessionToken = request.getHeader(SESSION_TOKEN_HEADER);
        return StringUtils.hasText(sessionToken) ? sessionToken : null;
    }

    private boolean belongsToChat(ChatParticipant participant, Chat chat) {
        return participant.getChat().getId().equals(chat.getId());
    }
}

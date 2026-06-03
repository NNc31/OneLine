package com.nefodov.oneline.security;

import com.nefodov.oneline.chat.*;
import com.nefodov.oneline.config.OneLineProperties;
import com.nefodov.oneline.security.SessionCookieFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
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
    private static final Pattern CHAT_PATH = Pattern.compile("^(?:/api/chats|/c)/(?<publicId>[0-9a-fA-F-]{36})(?:/.*)?$");

    private final ChatService chatService;
    private final ChatParticipantService participantService;
    private final OneLineProperties properties;
    private final SessionCookieFactory sessionCookieFactory;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String sessionToken = readSessionCookie(request);
        authenticate(request, sessionToken).ifPresent(auth -> {
            SecurityContextHolder.getContext().setAuthentication(auth);
            response.addHeader(HttpHeaders.SET_COOKIE, sessionCookieFactory.build(sessionToken).toString());
        });
        chain.doFilter(request, response);
    }

    private Optional<MagicLinkAuthentication> authenticate(HttpServletRequest request, String sessionToken) {
        UUID publicId = extractPublicId(request);
        if (publicId == null) {
            return Optional.empty();
        }
        String chatToken = request.getHeader(CHAT_TOKEN_HEADER);
        if (chatToken == null || chatToken.isBlank()) {
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

    private String readSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = properties.session().cookieName();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean belongsToChat(ChatParticipant participant, Chat chat) {
        return participant.getChat().getId().equals(chat.getId());
    }
}

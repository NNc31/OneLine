package com.nefodov.oneline.security;

import com.nefodov.oneline.chat.ChatParticipant;
import com.nefodov.oneline.chat.ChatParticipantService;
import com.nefodov.oneline.chat.ChatSession;
import com.nefodov.oneline.support.HkdfKeyDerivation;
import com.nefodov.oneline.support.OneLineProperties;
import com.nefodov.oneline.support.TokenHasher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@AllArgsConstructor
public class MagicLinkAuthenticationFilter extends OncePerRequestFilter {

    private static final Pattern CHAT_PATH = Pattern.compile("^(?:/api/chats|/c)/(?<token>[A-Za-z0-9_-]+)(?:/.*)?$");

    private final ChatParticipantService participantService;
    private final TokenHasher tokenHasher;
    private final HkdfKeyDerivation keyDerivation;
    private final OneLineProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        authenticate(request).ifPresent(auth -> SecurityContextHolder.getContext().setAuthentication(auth));
        chain.doFilter(request, response);
    }

    private Optional<MagicLinkAuthentication> authenticate(HttpServletRequest request) {
        String chatToken = extractChatToken(request);
        if (chatToken == null) {
            return Optional.empty();
        }
        String sessionToken = readSessionCookie(request);
        if (sessionToken == null) {
            return Optional.empty();
        }
        byte[] chatHash = tokenHasher.hash(chatToken);
        return participantService.resolveBySession(sessionToken)
                .filter(participant -> matchesChat(participant, chatHash))
                .map(participant -> {
                    byte[] messageKey = keyDerivation.deriveChatMessageKey(chatToken, participant.getChat().getId());
                    return new MagicLinkAuthentication(new ChatSession(participant.getChat(), participant, messageKey));
                });
    }

    private String extractChatToken(HttpServletRequest request) {
        Matcher matcher = CHAT_PATH.matcher(request.getRequestURI());
        return matcher.matches() ? matcher.group("token") : null;
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

    private boolean matchesChat(ChatParticipant participant, byte[] chatHash) {
        return MessageDigest.isEqual(participant.getChat().getChatTokenHash(), chatHash);
    }
}

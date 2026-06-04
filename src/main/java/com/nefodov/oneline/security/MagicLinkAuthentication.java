package com.nefodov.oneline.security;

import com.nefodov.oneline.chat.ChatSession;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

public class MagicLinkAuthentication extends AbstractAuthenticationToken {

    private final transient ChatSession session;

    public MagicLinkAuthentication(ChatSession session) {
        super(List.of());
        this.session = session;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return session;
    }

    public ChatSession session() {
        return session;
    }
}

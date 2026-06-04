package com.nefodov.oneline.security;

import com.nefodov.oneline.chat.ChatSession;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MagicLinkAuthentication other)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(session, other.session);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), session);
    }
}

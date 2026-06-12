package com.nefodov.oneline.security;

import com.nefodov.oneline.chat.Chat;
import com.nefodov.oneline.chat.ChatParticipant;
import com.nefodov.oneline.chat.ChatSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicLinkAuthenticationTest {

    @Test
    @DisplayName("Sets the chat session with the principal and is authenticated by construction")
    void exposesPrincipalAndAuthenticated() {
        ChatSession session = newSession();
        MagicLinkAuthentication auth = new MagicLinkAuthentication(session);
        assertSame(session, auth.getPrincipal());
        assertSame(session, auth.session());
        assertNull(auth.getCredentials());
        assertTrue(auth.isAuthenticated());
    }

    @Test
    @DisplayName("Two authentications wrapping the same session are equal and share a hash")
    void equalsForSameSession() {
        ChatSession session = newSession();
        MagicLinkAuthentication a = new MagicLinkAuthentication(session);
        MagicLinkAuthentication b = new MagicLinkAuthentication(session);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("Authentications with different sessions are not equal")
    void notEqualForDifferentSession() {
        MagicLinkAuthentication a = new MagicLinkAuthentication(newSession());
        MagicLinkAuthentication b = new MagicLinkAuthentication(newSession());
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("equals returns true for the same reference and false for foreign types")
    void equalsHandlesForeignTypes() {
        MagicLinkAuthentication a = new MagicLinkAuthentication(newSession());
        assertEquals(a, a);
        assertNotEquals(null, a);
        assertNotEquals("Not authentication", a);
    }

    private static ChatSession newSession() {
        Chat chat = new Chat();
        chat.setPublicId(UUID.randomUUID());
        ChatParticipant participant = new ChatParticipant();
        participant.setChat(chat);
        return new ChatSession(chat, participant);
    }
}

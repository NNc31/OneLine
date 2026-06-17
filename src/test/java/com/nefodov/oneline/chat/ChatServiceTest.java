package com.nefodov.oneline.chat;

import com.nefodov.oneline.exception.NotFoundException;
import com.nefodov.oneline.security.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private static final byte[] TOKEN_HASH = {1, 2, 3, 4};

    private ChatRepository chatRepository;
    private TokenHasher tokenHasher;
    private ChatService service;

    @BeforeEach
    void setUp() {
        chatRepository = mock(ChatRepository.class);
        tokenHasher = mock(TokenHasher.class);
        service = new ChatService(chatRepository, tokenHasher);
    }

    @Test
    @DisplayName("create stores a chat with a fresh public id, hashed token and TTL")
    void createPersistsChat() {
        when(tokenHasher.hash("auth")).thenReturn(TOKEN_HASH);
        when(chatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Chat chat = service.create("auth", 3600L);
        assertNotNull(chat.getPublicId());
        assertArrayEquals(TOKEN_HASH, chat.getChatTokenHash());
        assertEquals(3600L, chat.getMessageTtlSeconds());
    }

    @Test
    @DisplayName("findActive returns the chat when the token matches")
    void findActiveReturnsChat() {
        UUID publicId = UUID.randomUUID();
        Chat chat = chatWithHash();
        when(chatRepository.findByPublicId(publicId)).thenReturn(Optional.of(chat));
        when(tokenHasher.hash("token")).thenReturn(TOKEN_HASH);
        assertSame(chat, service.findActive(publicId, "token"));
    }

    @Test
    @DisplayName("findActive throws 404 when the chat is unknown")
    void findActiveThrowsWhenMissing() {
        UUID publicId = UUID.randomUUID();
        when(chatRepository.findByPublicId(publicId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.findActive(publicId, "token"));
    }

    @Test
    @DisplayName("findActive throws 404 when the chat is closed")
    void findActiveThrowsWhenClosed() {
        UUID publicId = UUID.randomUUID();
        Chat chat = chatWithHash();
        chat.setClosedAt(Instant.parse("2020-01-01T00:00:00Z"));
        when(chatRepository.findByPublicId(publicId)).thenReturn(Optional.of(chat));
        assertThrows(NotFoundException.class, () -> service.findActive(publicId, "token"));
    }

    @Test
    @DisplayName("findActive throws 404 when the token is null")
    void findActiveThrowsWhenTokenNull() {
        UUID publicId = UUID.randomUUID();
        Chat chat = chatWithHash();
        when(chatRepository.findByPublicId(publicId)).thenReturn(Optional.of(chat));
        assertThrows(NotFoundException.class, () -> service.findActive(publicId, null));
    }

    @Test
    @DisplayName("findActive throws 404 when the token hash does not match")
    void findActiveThrowsWhenTokenMismatch() {
        UUID publicId = UUID.randomUUID();
        Chat chat = chatWithHash();
        when(chatRepository.findByPublicId(publicId)).thenReturn(Optional.of(chat));
        when(tokenHasher.hash("wrong")).thenReturn(new byte[]{9, 9, 9, 9});
        assertThrows(NotFoundException.class, () -> service.findActive(publicId, "wrong"));
    }

    @Test
    @DisplayName("findByPublicId returns an open chat")
    void findByPublicIdReturnsChat() {
        UUID publicId = UUID.randomUUID();
        Chat chat = chatWithHash();
        when(chatRepository.findByPublicId(publicId)).thenReturn(Optional.of(chat));
        assertSame(chat, service.findByPublicId(publicId));
    }

    @Test
    @DisplayName("findByPublicId throws 404 when missing")
    void findByPublicIdThrowsWhenMissing() {
        UUID publicId = UUID.randomUUID();
        when(chatRepository.findByPublicId(publicId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.findByPublicId(publicId));
    }

    @Test
    @DisplayName("findByPublicId throws 404 when the chat is closed")
    void findByPublicIdThrowsWhenClosed() {
        UUID publicId = UUID.randomUUID();
        Chat chat = chatWithHash();
        chat.setClosedAt(Instant.parse("2020-01-01T00:00:00Z"));
        when(chatRepository.findByPublicId(publicId)).thenReturn(Optional.of(chat));
        assertThrows(NotFoundException.class, () -> service.findByPublicId(publicId));
    }

    @Test
    @DisplayName("deleteInactiveBefore delegates the cutoff to the repository")
    void deleteInactiveBeforeDelegates() {
        Instant cutoff = Instant.parse("2025-01-01T00:00:00Z");
        when(chatRepository.deleteInactiveBefore(cutoff)).thenReturn(5);
        assertEquals(5, service.deleteInactiveBefore(cutoff));
    }

    private static Chat chatWithHash() {
        Chat chat = new Chat();
        chat.setPublicId(UUID.randomUUID());
        chat.setChatTokenHash(ChatServiceTest.TOKEN_HASH);
        return chat;
    }
}

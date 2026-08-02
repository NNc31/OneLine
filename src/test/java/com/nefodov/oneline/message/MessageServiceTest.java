package com.nefodov.oneline.message;

import com.nefodov.oneline.chat.Chat;
import com.nefodov.oneline.chat.ChatParticipant;
import com.nefodov.oneline.chat.ChatSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MessageServiceTest {

    private static final byte[] CONTENT = {1, 2, 3};

    private MessageRepository messageRepository;
    private MessageService service;
    private Chat chat;
    private ChatParticipant participant;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        service = new MessageService(messageRepository);
        chat = new Chat();
        chat.setId(7L);
        chat.setPublicId(UUID.randomUUID());
        participant = new ChatParticipant();
        participant.setId(3L);
        participant.setChat(chat);
        participant.setDisplayName("Maelle");
        session = new ChatSession(chat, participant);
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Persists a new message with necessary fields")
    void sendPersistsMessage() {
        UUID clientMessageId = UUID.randomUUID();
        when(messageRepository.findByChatAndClientMessageId(chat, clientMessageId)).thenReturn(Optional.empty());

        Message stored = service.send(session, clientMessageId, CONTENT);

        assertSame(chat, stored.getChat());
        assertSame(participant, stored.getParticipant());
        assertEquals(clientMessageId, stored.getClientMessageId());
        assertArrayEquals(CONTENT, stored.getContent());
        assertEquals("chat", stored.getType());
    }

    @Test
    @DisplayName("Is idempotent and returns the stored message")
    void sendReturnsExistingMessageForDuplicate() {
        UUID clientMessageId = UUID.randomUUID();
        Message existing = new Message();
        when(messageRepository.findByChatAndClientMessageId(chat, clientMessageId)).thenReturn(Optional.of(existing));

        assertSame(existing, service.send(session, clientMessageId, CONTENT));
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects null content")
    void sendRejectsNullContent() {
        UUID clientMessageId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> service.send(session, clientMessageId, null));
    }

    @Test
    @DisplayName("Rejects empty content")
    void sendRejectsEmptyContent() {
        UUID clientMessageId = UUID.randomUUID();
        byte[] empty = new byte[0];
        assertThrows(IllegalArgumentException.class, () -> service.send(session, clientMessageId, empty));
    }

    @Test
    @DisplayName("Rejects content beyond the maximum length")
    void sendRejectsTooLongContent() {
        UUID clientMessageId = UUID.randomUUID();
        byte[] tooLong = new byte[8193];
        assertThrows(IllegalArgumentException.class, () -> service.send(session, clientMessageId, tooLong));
    }

    @Test
    @DisplayName("Stores a joined system message with empty content")
    void createJoinNoticeStoresSystemMessage() {
        Message notice = service.createJoinNotice(chat, participant);
        assertSame(chat, notice.getChat());
        assertSame(participant, notice.getParticipant());
        assertEquals("joined", notice.getType());
        assertEquals(0, notice.getContent().length);
        assertNotNull(notice.getClientMessageId());
    }

    @Test
    @DisplayName("Generates a distinct client id per notice")
    void createJoinNoticeGeneratesDistinctClientIds() {
        Message first = service.createJoinNotice(chat, participant);
        Message second = service.createJoinNotice(chat, participant);
        assertNotEquals(first.getClientMessageId(), second.getClientMessageId());
    }

    @Test
    @DisplayName("Reads the newest page with the default limit without a cursor")
    void historyUsesDefaultLimit() {
        when(messageRepository.findByChatOrderByIdDesc(eq(chat), any())).thenReturn(List.of());
        service.history(session, null, null);
        ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);
        verify(messageRepository).findByChatOrderByIdDesc(eq(chat), limit.capture());
        assertEquals(50, limit.getValue().max());
    }

    @Test
    @DisplayName("Pages backwards from the given id with a cursor")
    void historyPagesBeforeCursor() {
        when(messageRepository.findByChatAndIdLessThanOrderByIdDesc(eq(chat), eq(42L), any())).thenReturn(List.of());
        service.history(session, 42L, 10);
        ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);
        verify(messageRepository).findByChatAndIdLessThanOrderByIdDesc(eq(chat), eq(42L), limit.capture());
        assertEquals(10, limit.getValue().max());
    }

    @Test
    @DisplayName("Clamps a limit above the maximum")
    void historyClampsLimitAboveMaximum() {
        when(messageRepository.findByChatOrderByIdDesc(eq(chat), any())).thenReturn(List.of());
        service.history(session, null, 5000);
        ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);
        verify(messageRepository).findByChatOrderByIdDesc(eq(chat), limit.capture());
        assertEquals(200, limit.getValue().max());
    }

    @Test
    @DisplayName("Clamps a non-positive limit to one")
    void historyClampsLimitBelowMinimum() {
        when(messageRepository.findByChatOrderByIdDesc(eq(chat), any())).thenReturn(List.of());
        service.history(session, null, 0);
        ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);
        verify(messageRepository).findByChatOrderByIdDesc(eq(chat), limit.capture());
        assertEquals(1, limit.getValue().max());
    }

    @Test
    @DisplayName("Delegates the TTL sweep to the repository")
    void deleteExpiredDelegates() {
        when(messageRepository.deleteExpiredByChatTtl()).thenReturn(4);
        assertEquals(4, service.deleteExpired());
    }
}

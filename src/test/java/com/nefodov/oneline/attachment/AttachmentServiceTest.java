package com.nefodov.oneline.attachment;

import com.nefodov.oneline.attachment.dto.AttachmentDownloadResponse;
import com.nefodov.oneline.attachment.dto.AttachmentUploadResponse;
import com.nefodov.oneline.chat.Chat;
import com.nefodov.oneline.chat.ChatParticipant;
import com.nefodov.oneline.chat.ChatSession;
import com.nefodov.oneline.config.OneLineProperties;
import com.nefodov.oneline.web.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttachmentServiceTest {

    private static final long MAX_FILE_SIZE = 1_000L;

    private AttachmentRepository repository;
    private AttachmentStorage storage;
    private AttachmentService service;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        repository = mock(AttachmentRepository.class);
        storage = mock(AttachmentStorage.class);
        OneLineProperties properties = propertiesWithMaxFileSize(MAX_FILE_SIZE);
        service = new AttachmentService(repository, storage, properties);
        session = newSession();
    }

    @Test
    @DisplayName("prepareUpload creates one chunk per size and presigns a PUT for each")
    void prepareUploadCreatesChunks() {
        when(storage.presignPut(anyString())).thenReturn("http://put");
        when(repository.save(any())).thenAnswer(inv -> {
            Attachment a = inv.getArgument(0);
            a.setId(42L);
            return a;
        });

        AttachmentUploadResponse response = service.prepareUpload(session, List.of(100L, 200L));

        assertEquals(42L, response.attachmentId());
        assertEquals(2, response.chunks().size());
        assertEquals(0, response.chunks().get(0).index());
        assertEquals(1, response.chunks().get(1).index());
        verify(storage, times(2)).presignPut(anyString());

        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(repository).save(captor.capture());
        Attachment saved = captor.getValue();
        assertEquals(300L, saved.getCiphertextSize());
        assertFalse(saved.isConfirmed());
        assertEquals(2, saved.getChunks().size());
    }

    @Test
    @DisplayName("prepareUpload rejects a non-positive chunk size")
    void prepareUploadRejectsNonPositiveChunk() {
        List<Long> list = List.of(100L, 0L);
        assertThrows(IllegalArgumentException.class, () -> service.prepareUpload(session, list));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("prepareUpload rejects a total exceeding the max file size")
    void prepareUploadRejectsOverMax() {
        List<Long> list = List.of(600L, 600L);
        assertThrows(IllegalArgumentException.class, () -> service.prepareUpload(session, list));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("confirm marks a chunked attachment confirmed and records the real size")
    void confirmChunkedSucceeds() {
        Attachment attachment = chunkedAttachment("k0", "k1");
        when(repository.findByIdAndChat(1L, session.chat())).thenReturn(Optional.of(attachment));
        when(storage.objectSize("k0")).thenReturn(OptionalLong.of(120L));
        when(storage.objectSize("k1")).thenReturn(OptionalLong.of(80L));

        service.confirm(session, 1L);

        assertTrue(attachment.isConfirmed());
        assertEquals(200L, attachment.getCiphertextSize());
    }

    @Test
    @DisplayName("confirm throws 404 when a chunk object is missing")
    void confirmThrowsWhenChunkMissing() {
        Attachment attachment = chunkedAttachment("k0", "k1");
        when(repository.findByIdAndChat(1L, session.chat())).thenReturn(Optional.of(attachment));
        when(storage.objectSize("k0")).thenReturn(OptionalLong.of(120L));
        when(storage.objectSize("k1")).thenReturn(OptionalLong.empty());

        assertThrows(NotFoundException.class, () -> service.confirm(session, 1L));
        assertFalse(attachment.isConfirmed());
    }

    @Test
    @DisplayName("confirm removes the whole attachment and throws when chunks exceed the max size")
    void confirmRemovesOnOverflow() {
        Attachment attachment = chunkedAttachment("k0", "k1");
        when(repository.findByIdAndChat(1L, session.chat())).thenReturn(Optional.of(attachment));
        when(storage.objectSize("k0")).thenReturn(OptionalLong.of(900L));
        when(storage.objectSize("k1")).thenReturn(OptionalLong.of(900L));

        assertThrows(IllegalArgumentException.class, () -> service.confirm(session, 1L));
        verify(storage).remove(List.of("k0", "k1"));
        verify(repository).delete(attachment);
    }

    @Test
    @DisplayName("confirm of a legacy single-object attachment stats and confirms it")
    void confirmLegacySucceeds() {
        Attachment legacy = legacyAttachment("legacy-key");
        when(repository.findByIdAndChat(1L, session.chat())).thenReturn(Optional.of(legacy));
        when(storage.objectSize("legacy-key")).thenReturn(OptionalLong.of(64L));

        service.confirm(session, 1L);

        assertTrue(legacy.isConfirmed());
        assertEquals(64L, legacy.getCiphertextSize());
    }

    @Test
    @DisplayName("confirm throws 404 when the attachment does not belong to the chat")
    void confirmThrowsWhenNotFound() {
        when(repository.findByIdAndChat(eq(99L), any())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.confirm(session, 99L));
    }

    @Test
    @DisplayName("presignDownload returns one presigned GET per chunk in index order")
    void presignDownloadChunked() {
        Attachment attachment = chunkedAttachment("k0", "k1");
        attachment.setConfirmed(true);
        when(repository.findByIdAndChat(1L, session.chat())).thenReturn(Optional.of(attachment));
        when(storage.presignGet("k0")).thenReturn("get-0");
        when(storage.presignGet("k1")).thenReturn("get-1");

        AttachmentDownloadResponse response = service.presignDownload(session, 1L);

        assertEquals(2, response.chunks().size());
        assertEquals(0, response.chunks().get(0).index());
        assertEquals("get-0", response.chunks().get(0).downloadUrl());
        assertEquals("get-1", response.chunks().get(1).downloadUrl());
    }

    @Test
    @DisplayName("presignDownload returns a single synthetic chunk for a legacy attachment")
    void presignDownloadLegacy() {
        Attachment legacy = legacyAttachment("legacy-key");
        legacy.setConfirmed(true);
        when(repository.findByIdAndChat(1L, session.chat())).thenReturn(Optional.of(legacy));
        when(storage.presignGet("legacy-key")).thenReturn("get-legacy");

        AttachmentDownloadResponse response = service.presignDownload(session, 1L);

        assertEquals(1, response.chunks().size());
        assertEquals("get-legacy", response.chunks().get(0).downloadUrl());
    }

    @Test
    @DisplayName("presignDownload throws 404 for an unconfirmed attachment")
    void presignDownloadRejectsUnconfirmed() {
        Attachment attachment = chunkedAttachment("k0");
        attachment.setConfirmed(false);
        when(repository.findByIdAndChat(1L, session.chat())).thenReturn(Optional.of(attachment));
        assertThrows(NotFoundException.class, () -> service.presignDownload(session, 1L));
    }

    private Attachment chunkedAttachment(String... objectKeys) {
        Attachment attachment = new Attachment();
        attachment.setId(1L);
        attachment.setChat(session.chat());
        attachment.setParticipant(session.participant());
        attachment.setObjectKey(null);
        for (int i = 0; i < objectKeys.length; i++) {
            AttachmentChunk chunk = new AttachmentChunk();
            chunk.setChunkIndex(i);
            chunk.setObjectKey(objectKeys[i]);
            chunk.setCiphertextSize(0L);
            attachment.addChunk(chunk);
        }
        return attachment;
    }

    private Attachment legacyAttachment(String objectKey) {
        Attachment attachment = new Attachment();
        attachment.setId(1L);
        attachment.setChat(session.chat());
        attachment.setParticipant(session.participant());
        attachment.setObjectKey(objectKey);
        return attachment;
    }

    private static ChatSession newSession() {
        Chat chat = new Chat();
        chat.setId(7L);
        chat.setPublicId(UUID.randomUUID());
        ChatParticipant participant = new ChatParticipant();
        participant.setId(3L);
        participant.setChat(chat);
        participant.setDisplayName("Tester");
        return new ChatSession(chat, participant);
    }

    private static OneLineProperties propertiesWithMaxFileSize(long maxFileSize) {
        OneLineProperties.Storage storage = new OneLineProperties.Storage(
                "http://minio", "http://minio", "ak", "sk", "bucket",
                Duration.ofMinutes(30), maxFileSize, Duration.ofMinutes(30));
        return new OneLineProperties(null, null, null, storage, null);
    }
}

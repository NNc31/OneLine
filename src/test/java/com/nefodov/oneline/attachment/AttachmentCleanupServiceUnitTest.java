package com.nefodov.oneline.attachment;

import com.nefodov.oneline.config.OneLineProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class AttachmentCleanupServiceUnitTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private AttachmentRepository repository;
    private AttachmentStorage storage;

    @BeforeEach
    void setUp() {
        repository = mock(AttachmentRepository.class);
        storage = mock(AttachmentStorage.class);
    }

    @Test
    @DisplayName("Null attachment TTL disables the sweep instead of deleting everything")
    void attachmentTtlSweepSkipsWhenTtlIsNull() {
        assertEquals(0, cleanupWithTtl(null).sweepExpiredByAttachmentTtl());
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Zero attachment TTL disables the sweep")
    void attachmentTtlSweepSkipsWhenTtlIsZero() {
        assertEquals(0, cleanupWithTtl(Duration.ZERO).sweepExpiredByAttachmentTtl());
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Negative attachment TTL disables the sweep")
    void attachmentTtlSweepSkipsWhenTtlIsNegative() {
        assertEquals(0, cleanupWithTtl(Duration.ofDays(-1)).sweepExpiredByAttachmentTtl());
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Sweep that matches no rows touches neither storage nor the database")
    void sweepWithoutMatchesTouchesNothing() {
        when(repository.findUnconfirmedAttachmentIdsOlderThan(any())).thenReturn(List.of());
        assertEquals(0, cleanupWithTtl(Duration.ofDays(31)).sweepUnconfirmed());
        verify(storage, never()).remove(anyCollection());
        verify(repository, never()).deleteByIds(anyCollection());
    }

    @Test
    @DisplayName("Sweep deletes the objects first and the rows second")
    void sweepRemovesObjectsThenRows() {
        List<Long> ids = List.of(4L, 5L);
        when(repository.findExpiredAttachmentIdsByChatTtl()).thenReturn(ids);
        when(repository.findAllObjectKeysByAttachmentIds(ids)).thenReturn(List.of("k0", "k1"));
        when(repository.deleteByIds(ids)).thenReturn(2);
        assertEquals(2, cleanupWithTtl(Duration.ofDays(31)).sweepExpiredByChatTtl());
        InOrder order = inOrder(storage, repository);
        order.verify(storage).remove(List.of("k0", "k1"));
        order.verify(repository).deleteByIds(ids);
    }

    @Test
    @DisplayName("Inactive chats with no attachments leave storage alone")
    void inactiveChatCleanupSkipsStorageWhenNothingMatches() {
        when(repository.findAttachmentIdsForInactiveChatsBefore(any())).thenReturn(List.of());
        assertEquals(0, cleanupWithTtl(Duration.ofDays(31)).removeObjectsForInactiveChats(NOW));
        verify(storage, never()).remove(anyCollection());
    }

    @Test
    @DisplayName("Inactive chats whose rows carry no object keys are still reported as handled")
    void inactiveChatCleanupReportsRowsWithoutObjects() {
        List<Long> ids = List.of(9L);
        when(repository.findAttachmentIdsForInactiveChatsBefore(any())).thenReturn(ids);
        when(repository.findAllObjectKeysByAttachmentIds(ids)).thenReturn(List.of());
        assertEquals(1, cleanupWithTtl(Duration.ofDays(31)).removeObjectsForInactiveChats(NOW));
        verify(storage, never()).remove(anyCollection());
    }

    private AttachmentCleanupService cleanupWithTtl(Duration attachmentTtl) {
        OneLineProperties.Storage storageProps = new OneLineProperties.Storage(
                "http://minio", "http://minio", "ak", "sk", "bucket", Duration.ofMinutes(30), 1024L, Duration.ofMinutes(30));
        OneLineProperties properties = new OneLineProperties(null, null, null, storageProps,
                new OneLineProperties.Attachments(true, attachmentTtl, 3000L, 5368709120L));
        return new AttachmentCleanupService(repository, storage, properties, CLOCK);
    }
}

package com.nefodov.oneline.attachment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class AttachmentExpirySchedulerTest {

    private AttachmentCleanupService cleanupService;
    private AttachmentExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        cleanupService = mock(AttachmentCleanupService.class);
        scheduler = new AttachmentExpiryScheduler(cleanupService);
    }

    @Test
    @DisplayName("Sweep runs all three cleanups even when the first one finds nothing")
    void sweepRunsEveryCleanup() {
        when(cleanupService.sweepExpiredByChatTtl()).thenReturn(0);
        when(cleanupService.sweepUnconfirmed()).thenReturn(0);
        when(cleanupService.sweepExpiredByAttachmentTtl()).thenReturn(0);
        scheduler.sweep();
        verify(cleanupService).sweepExpiredByChatTtl();
        verify(cleanupService).sweepUnconfirmed();
        verify(cleanupService).sweepExpiredByAttachmentTtl();
    }

    @Test
    @DisplayName("Sweep that removed something by chat TTL still runs the remaining cleanups")
    void sweepReportsChatTtlRemovals() {
        when(cleanupService.sweepExpiredByChatTtl()).thenReturn(4);
        when(cleanupService.sweepUnconfirmed()).thenReturn(0);
        when(cleanupService.sweepExpiredByAttachmentTtl()).thenReturn(0);
        scheduler.sweep();
        verify(cleanupService).sweepUnconfirmed();
        verify(cleanupService).sweepExpiredByAttachmentTtl();
    }

    @Test
    @DisplayName("Sweep that only removed attachment-TTL leftovers is reported too")
    void sweepReportsAttachmentTtlRemovals() {
        when(cleanupService.sweepExpiredByChatTtl()).thenReturn(0);
        when(cleanupService.sweepUnconfirmed()).thenReturn(0);
        when(cleanupService.sweepExpiredByAttachmentTtl()).thenReturn(2);
        scheduler.sweep();
        verify(cleanupService).sweepExpiredByAttachmentTtl();
    }
}

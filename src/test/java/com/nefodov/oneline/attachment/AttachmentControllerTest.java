package com.nefodov.oneline.attachment;

import com.nefodov.oneline.attachment.dto.AttachmentUploadRequest;
import com.nefodov.oneline.attachment.dto.AttachmentUploadResponse;
import com.nefodov.oneline.chat.Chat;
import com.nefodov.oneline.chat.ChatParticipant;
import com.nefodov.oneline.chat.ChatSession;
import com.nefodov.oneline.config.OneLineProperties;
import com.nefodov.oneline.exception.NotFoundException;
import com.nefodov.oneline.exception.TooManyRequestsException;
import com.nefodov.oneline.ratelimit.RateLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttachmentControllerTest {

    private static final String PARTICIPANT_KEY = "p:3";
    private static final String ADDRESS_KEY = "ip:203.0.113.7";

    private AttachmentService attachmentService;
    private RateLimiter rateLimiter;
    private AttachmentController controller;
    private ChatSession session;
    private HttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        attachmentService = mock(AttachmentService.class);
        rateLimiter = mock(RateLimiter.class);
        httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getRemoteAddr()).thenReturn("203.0.113.7");
        when(rateLimiter.tryAcquire(anyString(), anyString(), anyLong())).thenReturn(true);
        session = newSession();
        controller = newController(attachmentsEnabled(true));
    }

    @Test
    @DisplayName("prepare charges every bucket to the participant and to the address it came from")
    void prepareChargesBothKeys() {
        when(attachmentService.prepareUpload(eq(session), anyList())).thenReturn(new AttachmentUploadResponse(1L, List.of()));
        controller.prepare(session.chat().getPublicId(), new AttachmentUploadRequest(List.of(100L, 200L)), session, httpRequest);

        for (String key : List.of(PARTICIPANT_KEY, ADDRESS_KEY)) {
            verify(rateLimiter).tryAcquire("attachment", key, 1L);
            verify(rateLimiter).tryAcquire("attachment-objects", key, 2L);
            verify(rateLimiter).tryAcquire("upload-bytes", key, 300L);
        }
    }

    @Test
    @DisplayName("prepare is refused once the object budget is spent, before anything is stored")
    void prepareRejectsWhenObjectBudgetIsSpent() {
        when(rateLimiter.tryAcquire(eq("attachment-objects"), anyString(), anyLong())).thenReturn(false);
        UUID publicId = session.chat().getPublicId();
        AttachmentUploadRequest request = new AttachmentUploadRequest(List.of(100L));
        assertThrows(TooManyRequestsException.class, () -> controller.prepare(publicId, request, session, httpRequest));
        verify(attachmentService, never()).prepareUpload(any(), anyList());
    }

    @Test
    @DisplayName("prepare refuses a chat the session does not belong to")
    void prepareRejectsForeignChat() {
        UUID foreign = UUID.randomUUID();
        AttachmentUploadRequest request = new AttachmentUploadRequest(List.of(100L));
        assertThrows(NotFoundException.class, () -> controller.prepare(foreign, request, session, httpRequest));
        verify(rateLimiter, never()).tryAcquire(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("prepare answers 503 while uploads are switched off")
    void prepareRejectsWhileUploadsAreDisabled() {
        controller = newController(attachmentsEnabled(false));
        UUID publicId = session.chat().getPublicId();
        AttachmentUploadRequest request = new AttachmentUploadRequest(List.of(100L));
        assertThrows(ResponseStatusException.class, () -> controller.prepare(publicId, request, session, httpRequest));
    }

    @Test
    @DisplayName("confirm charges the bytes that were never declared")
    void confirmChargesUndeclaredBytes() {
        when(attachmentService.confirm(session, 5L)).thenReturn(4096L);
        controller.confirm(session.chat().getPublicId(), 5L, session, httpRequest);
        verify(rateLimiter).tryAcquire("upload-bytes", PARTICIPANT_KEY, 4096L);
        verify(rateLimiter).tryAcquire("upload-bytes", ADDRESS_KEY, 4096L);
        verify(attachmentService, never()).discard(any(), anyLong());
    }

    @Test
    @DisplayName("confirm leaves the quota alone when the upload matched its declaration")
    void confirmSkipsQuotaWhenNothingUndeclared() {
        when(attachmentService.confirm(session, 5L)).thenReturn(0L);
        controller.confirm(session.chat().getPublicId(), 5L, session, httpRequest);
        verify(rateLimiter, never()).tryAcquire(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("confirm throws away an upload whose undeclared bytes no longer fit the quota")
    void confirmDiscardsWhenUndeclaredBytesExceedQuota() {
        when(attachmentService.confirm(session, 5L)).thenReturn(4096L);
        when(rateLimiter.tryAcquire(eq("upload-bytes"), anyString(), anyLong())).thenReturn(false);
        UUID publicId = session.chat().getPublicId();
        assertThrows(TooManyRequestsException.class, () -> controller.confirm(publicId, 5L, session, httpRequest));
        verify(attachmentService).discard(session, 5L);
    }

    private AttachmentController newController(OneLineProperties properties) {
        return new AttachmentController(attachmentService, rateLimiter, new SimpleMeterRegistry(), properties);
    }

    private static OneLineProperties attachmentsEnabled(boolean enabled) {
        return new OneLineProperties(null, null, null, null,
                new OneLineProperties.Attachments(enabled, Duration.ofDays(31), 3000L, 5368709120L));
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
}

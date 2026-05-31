package com.nefodov.oneline.attachment;

import com.nefodov.oneline.attachment.dto.AttachmentDownloadResponse;
import com.nefodov.oneline.attachment.dto.AttachmentUploadRequest;
import com.nefodov.oneline.attachment.dto.AttachmentUploadResponse;
import com.nefodov.oneline.chat.ChatSession;
import com.nefodov.oneline.support.NotFoundException;
import com.nefodov.oneline.support.RateLimiter;
import com.nefodov.oneline.support.TooManyRequestsException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/chats/{publicId}/attachments")
@AllArgsConstructor
public class AttachmentController {

    private static final String BUCKET_ATTACHMENT = "attachment";

    private final AttachmentService attachmentService;
    private final RateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;

    @PostMapping
    public AttachmentUploadResponse prepare(@PathVariable UUID publicId, @Valid @RequestBody AttachmentUploadRequest request, @AuthenticationPrincipal ChatSession session) {
        verifyChat(publicId, session);
        enforceRateLimit(session);
        AttachmentService.PreparedUpload prepared = attachmentService.prepareUpload(session, request.size());
        meterRegistry.counter("oneline.attachments.prepared").increment();
        return new AttachmentUploadResponse(prepared.attachmentId(), prepared.uploadUrl());
    }

    @PostMapping("/{attachmentId}/confirm")
    public void confirm(@PathVariable UUID publicId, @PathVariable Long attachmentId, @AuthenticationPrincipal ChatSession session) {
        verifyChat(publicId, session);
        attachmentService.confirm(session, attachmentId);
        meterRegistry.counter("oneline.attachments.confirmed").increment();
    }

    @GetMapping("/{attachmentId}")
    public AttachmentDownloadResponse download(@PathVariable UUID publicId, @PathVariable Long attachmentId, @AuthenticationPrincipal ChatSession session) {
        verifyChat(publicId, session);
        return new AttachmentDownloadResponse(attachmentService.presignDownload(session, attachmentId));
    }

    private void verifyChat(UUID publicId, ChatSession session) {
        if (!session.chat().getPublicId().equals(publicId)) {
            throw new NotFoundException("Chat not found");
        }
    }

    private void enforceRateLimit(ChatSession session) {
        if (!rateLimiter.tryAcquire(BUCKET_ATTACHMENT, String.valueOf(session.participant().getId()))) {
            meterRegistry.counter("oneline.ratelimit.rejected", "bucket", BUCKET_ATTACHMENT).increment();
            throw new TooManyRequestsException("Too many uploads");
        }
    }
}

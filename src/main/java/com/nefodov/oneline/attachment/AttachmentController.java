package com.nefodov.oneline.attachment;

import com.nefodov.oneline.attachment.dto.AttachmentDownloadResponse;
import com.nefodov.oneline.attachment.dto.AttachmentUploadRequest;
import com.nefodov.oneline.attachment.dto.AttachmentUploadResponse;
import com.nefodov.oneline.chat.ChatSession;
import com.nefodov.oneline.config.OneLineProperties;
import com.nefodov.oneline.ratelimit.RateLimiter;
import com.nefodov.oneline.web.exception.NotFoundException;
import com.nefodov.oneline.web.exception.TooManyRequestsException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/chats/{publicId}/attachments")
@AllArgsConstructor
public class AttachmentController {

    private static final String BUCKET_ATTACHMENT = "attachment";
    private static final String BUCKET_UPLOAD_BYTES = "upload-bytes";

    private final AttachmentService attachmentService;
    private final RateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;
    private final OneLineProperties properties;

    @PostMapping
    public AttachmentUploadResponse prepare(@PathVariable("publicId") UUID publicId, @Valid @RequestBody AttachmentUploadRequest request, @AuthenticationPrincipal ChatSession session) {
        verifyChat(publicId, session);
        requireUploadEnabled();
        enforceRateLimit(session);
        long totalBytes = request.chunks().stream().mapToLong(Long::longValue).sum();
        enforceByteQuota(session, totalBytes);
        AttachmentUploadResponse response = attachmentService.prepareUpload(session, request.chunks());
        meterRegistry.counter("oneline.attachments.prepared").increment();
        return response;
    }

    @PostMapping("/{attachmentId}/confirm")
    public void confirm(@PathVariable("publicId") UUID publicId, @PathVariable("attachmentId") Long attachmentId, @AuthenticationPrincipal ChatSession session) {
        verifyChat(publicId, session);
        requireUploadEnabled();
        attachmentService.confirm(session, attachmentId);
        meterRegistry.counter("oneline.attachments.confirmed").increment();
    }

    @GetMapping("/{attachmentId}")
    public AttachmentDownloadResponse download(@PathVariable("publicId") UUID publicId, @PathVariable("attachmentId") Long attachmentId, @AuthenticationPrincipal ChatSession session) {
        verifyChat(publicId, session);
        return attachmentService.presignDownload(session, attachmentId);
    }

    private void verifyChat(UUID publicId, ChatSession session) {
        if (!session.chat().getPublicId().equals(publicId)) {
            throw new NotFoundException("Chat not found");
        }
    }

    private void requireUploadEnabled() {
        if (!properties.attachments().enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Attachments are temporarily disabled");
        }
    }

    private void enforceRateLimit(ChatSession session) {
        if (!rateLimiter.tryAcquire(BUCKET_ATTACHMENT, String.valueOf(session.participant().getId()))) {
            meterRegistry.counter("oneline.ratelimit.rejected", "bucket", BUCKET_ATTACHMENT).increment();
            throw new TooManyRequestsException("Too many uploads");
        }
    }

    private void enforceByteQuota(ChatSession session, long totalBytes) {
        if (!rateLimiter.tryAcquire(BUCKET_UPLOAD_BYTES, String.valueOf(session.participant().getId()), totalBytes)) {
            meterRegistry.counter("oneline.ratelimit.rejected", "bucket", BUCKET_UPLOAD_BYTES).increment();
            throw new TooManyRequestsException("Daily upload quota exceeded");
        }
    }
}

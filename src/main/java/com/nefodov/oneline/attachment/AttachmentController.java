package com.nefodov.oneline.attachment;

import com.nefodov.oneline.attachment.dto.AttachmentDownloadResponse;
import com.nefodov.oneline.attachment.dto.AttachmentUploadRequest;
import com.nefodov.oneline.attachment.dto.AttachmentUploadResponse;
import com.nefodov.oneline.chat.ChatSession;
import com.nefodov.oneline.config.OneLineProperties;
import com.nefodov.oneline.exception.NotFoundException;
import com.nefodov.oneline.exception.TooManyRequestsException;
import com.nefodov.oneline.ratelimit.RateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chats/{publicId}/attachments")
@AllArgsConstructor
public class AttachmentController {

    private static final String BUCKET_ATTACHMENT = "attachment";
    private static final String BUCKET_ATTACHMENT_OBJECTS = "attachment-objects";
    private static final String BUCKET_UPLOAD_BYTES = "upload-bytes";
    private static final String QUOTA_MESSAGE = "Daily upload quota exceeded";

    private final AttachmentService attachmentService;
    private final RateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;
    private final OneLineProperties properties;

    @PostMapping
    public AttachmentUploadResponse prepare(@PathVariable("publicId") UUID publicId, @Valid @RequestBody AttachmentUploadRequest request, @AuthenticationPrincipal ChatSession session, HttpServletRequest httpRequest) {
        verifyChat(publicId, session);
        requireUploadEnabled();
        List<String> keys = quotaKeys(session, httpRequest);
        enforce(BUCKET_ATTACHMENT, keys, 1L, "Too many uploads");
        enforce(BUCKET_ATTACHMENT_OBJECTS, keys, request.chunks().size(), QUOTA_MESSAGE);
        enforce(BUCKET_UPLOAD_BYTES, keys, request.chunks().stream().mapToLong(Long::longValue).sum(), QUOTA_MESSAGE);
        AttachmentUploadResponse response = attachmentService.prepareUpload(session, request.chunks());
        meterRegistry.counter("oneline.attachments.prepared").increment();
        return response;
    }

    @PostMapping("/{attachmentId}/confirm")
    public void confirm(@PathVariable("publicId") UUID publicId, @PathVariable("attachmentId") Long attachmentId, @AuthenticationPrincipal ChatSession session, HttpServletRequest httpRequest) {
        verifyChat(publicId, session);
        requireUploadEnabled();
        long undeclared = attachmentService.confirm(session, attachmentId);
        try {
            enforce(BUCKET_UPLOAD_BYTES, quotaKeys(session, httpRequest), undeclared, QUOTA_MESSAGE);
        } catch (TooManyRequestsException e) {
            attachmentService.discard(session, attachmentId);
            throw e;
        }
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

    private static List<String> quotaKeys(ChatSession session, HttpServletRequest request) {
        return List.of("p:" + session.participant().getId(), "ip:" + request.getRemoteAddr());
    }

    private void enforce(String bucket, List<String> keys, long tokens, String message) {
        if (tokens <= 0L) {
            return;
        }
        for (String key : keys) {
            if (!rateLimiter.tryAcquire(bucket, key, tokens)) {
                meterRegistry.counter("oneline.ratelimit.rejected", "bucket", bucket).increment();
                throw new TooManyRequestsException(message);
            }
        }
    }
}

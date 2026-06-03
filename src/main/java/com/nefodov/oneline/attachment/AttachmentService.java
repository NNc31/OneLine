package com.nefodov.oneline.attachment;

import com.nefodov.oneline.chat.ChatSession;
import com.nefodov.oneline.web.exception.NotFoundException;
import com.nefodov.oneline.config.OneLineProperties;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

@Service
@AllArgsConstructor
public class AttachmentService {

    private final AttachmentRepository repository;
    private final AttachmentStorage storage;
    private final OneLineProperties properties;

    @Transactional
    public PreparedUpload prepareUpload(ChatSession session, long declaredSize) {
        long max = properties.storage().maxFileSize();
        if (declaredSize <= 0 || declaredSize > max) {
            throw new IllegalArgumentException("File exceeds the maximum allowed size");
        }
        Attachment attachment = new Attachment();
        attachment.setChat(session.chat());
        attachment.setParticipant(session.participant());
        attachment.setObjectKey(UUID.randomUUID().toString());
        attachment.setCiphertextSize(declaredSize);
        attachment.setConfirmed(false);
        Attachment saved = repository.save(attachment);
        String uploadUrl = storage.presignPut(saved.getObjectKey());
        return new PreparedUpload(saved.getId(), uploadUrl);
    }

    @Transactional
    public void confirm(ChatSession session, Long attachmentId) {
        Attachment attachment = require(session, attachmentId);
        OptionalLong actualSize = storage.objectSize(attachment.getObjectKey());
        if (actualSize.isEmpty()) {
            throw new NotFoundException("Uploaded object not found");
        }
        if (actualSize.getAsLong() > properties.storage().maxFileSize()) {
            storage.remove(List.of(attachment.getObjectKey()));
            repository.delete(attachment);
            throw new IllegalArgumentException("Uploaded object exceeds the maximum allowed size");
        }
        attachment.setCiphertextSize(actualSize.getAsLong());
        attachment.setConfirmed(true);
    }

    @Transactional(readOnly = true)
    public String presignDownload(ChatSession session, Long attachmentId) {
        Attachment attachment = require(session, attachmentId);
        if (!attachment.isConfirmed()) {
            throw new NotFoundException("Attachment is not available");
        }
        return storage.presignGet(attachment.getObjectKey());
    }

    private Attachment require(ChatSession session, Long attachmentId) {
        return repository.findByIdAndChat(attachmentId, session.chat()).orElseThrow(() -> new NotFoundException("Attachment not found"));
    }

    public record PreparedUpload(Long attachmentId, String uploadUrl) {
    }
}

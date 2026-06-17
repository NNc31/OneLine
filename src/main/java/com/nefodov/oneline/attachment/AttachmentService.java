package com.nefodov.oneline.attachment;

import com.nefodov.oneline.attachment.dto.AttachmentDownloadResponse;
import com.nefodov.oneline.attachment.dto.AttachmentUploadResponse;
import com.nefodov.oneline.chat.ChatSession;
import com.nefodov.oneline.config.OneLineProperties;
import com.nefodov.oneline.exception.NotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@AllArgsConstructor
public class AttachmentService {

    private final AttachmentRepository repository;
    private final AttachmentStorage storage;
    private final OneLineProperties properties;

    @Transactional
    public AttachmentUploadResponse prepareUpload(ChatSession session, List<Long> chunkSizes) {
        Attachment attachment = getAttachment(session, chunkSizes);

        List<AttachmentUploadResponse.ChunkUpload> uploads = new ArrayList<>(chunkSizes.size());
        for (int i = 0; i < chunkSizes.size(); i++) {
            AttachmentChunk chunk = new AttachmentChunk();
            chunk.setChunkIndex(i);
            chunk.setObjectKey(UUID.randomUUID().toString());
            chunk.setCiphertextSize(chunkSizes.get(i));
            attachment.addChunk(chunk);
            uploads.add(new AttachmentUploadResponse.ChunkUpload(i, storage.presignPut(chunk.getObjectKey())));
        }
        Attachment saved = repository.save(attachment);
        return new AttachmentUploadResponse(saved.getId(), uploads);
    }

    private Attachment getAttachment(ChatSession session, List<Long> chunkSizes) {
        long total = 0L;
        for (Long size : chunkSizes) {
            if (size == null || size <= 0L) {
                throw new IllegalArgumentException("Chunk size must be positive");
            }
            total += size;
        }
        long max = properties.storage().maxFileSize();
        if (total > max) {
            throw new IllegalArgumentException("File exceeds the maximum allowed size");
        }

        Attachment attachment = new Attachment();
        attachment.setChat(session.chat());
        attachment.setParticipant(session.participant());
        attachment.setObjectKey(null);
        attachment.setCiphertextSize(total);
        attachment.setConfirmed(false);
        return attachment;
    }

    @Transactional
    public void confirm(ChatSession session, Long attachmentId) {
        Attachment attachment = require(session, attachmentId);

        // Process legacy attachment without chunks
        if (attachment.getChunks().isEmpty()) {
            confirmLegacy(attachment);
            return;
        }

        long total = 0L;
        long max = properties.storage().maxFileSize();
        for (AttachmentChunk chunk : attachment.getChunks()) {
            OptionalLong actual = storage.objectSize(chunk.getObjectKey());
            if (actual.isEmpty()) {
                throw new NotFoundException("Uploaded chunk not found: " + chunk.getChunkIndex());
            }
            chunk.setCiphertextSize(actual.getAsLong());
            total += actual.getAsLong();
            if (total > max) {
                List<String> keys = attachment.getChunks().stream().map(AttachmentChunk::getObjectKey).toList();
                storage.remove(keys);
                repository.delete(attachment);
                throw new IllegalArgumentException("Uploaded file exceeds the maximum allowed size");
            }
        }
        attachment.setCiphertextSize(total);
        attachment.setConfirmed(true);
    }

    @Transactional(readOnly = true)
    public AttachmentDownloadResponse presignDownload(ChatSession session, Long attachmentId) {
        Attachment attachment = require(session, attachmentId);
        if (!attachment.isConfirmed()) {
            throw new NotFoundException("Attachment is not available");
        }

        if (attachment.getChunks().isEmpty()) {
            if (attachment.getObjectKey() == null) {
                throw new NotFoundException("Attachment is not available");
            }
            return new AttachmentDownloadResponse(List.of(new AttachmentDownloadResponse.ChunkDownload(0, storage.presignGet(attachment.getObjectKey()))
            ));
        }

        List<AttachmentDownloadResponse.ChunkDownload> chunks = attachment.getChunks().stream()
                .sorted(Comparator.comparingInt(AttachmentChunk::getChunkIndex))
                .map(c -> new AttachmentDownloadResponse.ChunkDownload(c.getChunkIndex(), storage.presignGet(c.getObjectKey())))
                .toList();
        return new AttachmentDownloadResponse(chunks);
    }

    private void confirmLegacy(Attachment attachment) {
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

    private Attachment require(ChatSession session, Long attachmentId) {
        return repository.findByIdAndChat(attachmentId, session.chat()).orElseThrow(() -> new NotFoundException("Attachment not found"));
    }
}

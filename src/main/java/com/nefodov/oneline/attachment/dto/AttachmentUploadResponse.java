package com.nefodov.oneline.attachment.dto;

import java.util.List;

public record AttachmentUploadResponse(Long attachmentId, List<ChunkUpload> chunks) {

    public record ChunkUpload(int index, String uploadUrl) {
    }
}

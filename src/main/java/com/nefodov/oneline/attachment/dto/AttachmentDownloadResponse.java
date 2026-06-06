package com.nefodov.oneline.attachment.dto;

import java.util.List;

public record AttachmentDownloadResponse(List<ChunkDownload> chunks) {

    public record ChunkDownload(int index, String downloadUrl) {
    }
}

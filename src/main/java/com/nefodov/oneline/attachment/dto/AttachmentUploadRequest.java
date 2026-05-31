package com.nefodov.oneline.attachment.dto;

import jakarta.validation.constraints.Min;

public record AttachmentUploadRequest(@Min(1) long size) {
}

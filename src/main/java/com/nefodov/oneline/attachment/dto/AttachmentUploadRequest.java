package com.nefodov.oneline.attachment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AttachmentUploadRequest(@NotEmpty @Size(max = 300) List<@Min(1) Long> chunks) {
}

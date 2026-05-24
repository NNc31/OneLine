package com.nefodov.oneline.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateChatRequest(@NotBlank String authToken, @Min(60) @Max(2_592_000) Long messageTtlSeconds) {
}

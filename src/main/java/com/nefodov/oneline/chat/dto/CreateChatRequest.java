package com.nefodov.oneline.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateChatRequest(@NotBlank String authToken) {
}

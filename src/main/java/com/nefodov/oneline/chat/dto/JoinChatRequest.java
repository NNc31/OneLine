package com.nefodov.oneline.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinChatRequest(@NotBlank @Size(max = 40) String displayName) {
}

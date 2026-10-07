package com.team5.advisor.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/** Request and response bodies of advisor-api.yaml. */
public final class ChatDtos {

    private ChatDtos() {
    }

    public enum ChatRole {
        @JsonProperty("user") USER,
        @JsonProperty("assistant") ASSISTANT
    }

    public record ChatMessage(
        @NotNull ChatRole role,
        @NotBlank @Size(max = 4000) String content
    ) {
    }

    public record AdvisorChatRequest(
        @NotEmpty @Size(max = 20) List<@Valid @NotNull ChatMessage> messages
    ) {
    }

    public record AdvisorChatResponse(
        String reply,
        List<String> toolsUsed,
        String model,
        OffsetDateTime generatedAt
    ) {
    }

    public record ErrorResponse(String errorCode, String message) {
    }
}

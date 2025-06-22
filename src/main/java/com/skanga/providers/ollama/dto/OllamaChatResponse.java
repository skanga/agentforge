package com.skanga.providers.ollama.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// DTO for the non-streaming response from /api/chat
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaChatResponse(
    String model,
    @JsonProperty("created_at") String createdAt,
    OllamaChatMessage message, // The actual response message from the assistant
    Boolean done, // True if the response is complete (non-streaming)
    @JsonProperty("total_duration") Long totalDuration,
    @JsonProperty("load_duration") Long loadDuration,
    @JsonProperty("prompt_eval_count") Integer promptEvalCount,
    @JsonProperty("prompt_eval_duration") Long promptEvalDuration, // Added based on typical Ollama responses
    @JsonProperty("eval_count") Integer evalCount,
    @JsonProperty("eval_duration") Long evalDuration
) {}

package com.skanga.providers.ollama.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaChatMessage(
    String role, // "user", "assistant", "system"
    String content,
    List<String> images, // List of base64 encoded strings
    @JsonProperty("tool_calls") List<OllamaToolCall> toolCalls
) {}

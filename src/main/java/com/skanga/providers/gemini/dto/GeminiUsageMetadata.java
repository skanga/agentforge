
// gemini/dto/GeminiUsageMetadata.java
package com.skanga.providers.gemini.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GeminiUsageMetadata(
    @JsonProperty("prompt_token_count") Integer promptTokenCount,
    @JsonProperty("candidates_token_count") Integer candidatesTokenCount, // Sum of tokens for all candidates
    @JsonProperty("total_token_count") Integer totalTokenCount
) {}

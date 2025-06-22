
// gemini/dto/GeminiCandidate.java
package com.skanga.providers.gemini.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GeminiCandidate(
    GeminiContent content, // The actual message content from the model
    @JsonProperty("finish_reason") String finishReason, // e.g., "STOP", "MAX_TOKENS", "TOOL_CODE", "SAFETY"
    Integer index,
    @JsonProperty("safety_ratings") List<SafetyRating> safetyRatings,
    @JsonProperty("token_count") Integer tokenCount // Only for non-streaming, aggregated. Not in stream deltas.
    // GroundingMetadata groundingMetadata; // If grounding is used
) {
    public record SafetyRating(
        String category, // e.g., "HARM_CATEGORY_SEXUALLY_EXPLICIT"
        String probability // e.g., "NEGLIGIBLE", "LOW", "MEDIUM", "HIGH"
        // String probabilityScore; // More fine-grained score
        // Severity severity;
        // String severityScore;
    ) {}
}

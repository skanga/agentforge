
// gemini/dto/GenerateContentResponse.java
package com.skanga.providers.gemini.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GenerateContentResponse(
    List<GeminiCandidate> candidates,
    @JsonProperty("prompt_feedback") PromptFeedback promptFeedback,
    @JsonProperty("usage_metadata") GeminiUsageMetadata usageMetadata // Present in non-streaming responses
) {
    public record PromptFeedback(
        @JsonProperty("block_reason") String blockReason, // If the prompt was blocked
        @JsonProperty("safety_ratings") List<GeminiCandidate.SafetyRating> safetyRatings
        // String blockReasonMessage; // If available
    ) {}
}

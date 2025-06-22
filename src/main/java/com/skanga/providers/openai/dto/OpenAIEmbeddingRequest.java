
// openai/dto/OpenAIEmbeddingRequest.java
package com.skanga.providers.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenAIEmbeddingRequest(
    String input,
    String model,
    @JsonProperty("encoding_format") String encodingFormat, // e.g., "float" or "base64"
    Integer dimensions // Optional
) {
    // Constructor for typical float embeddings without optional dimensions
    public OpenAIEmbeddingRequest(String model, String input) {
        this(model, input, "float", null);
    }

    // Constructor for float embeddings with optional dimensions
    public OpenAIEmbeddingRequest(String model, String input, Integer dimensions) {
        this(model, input, "float", dimensions);
    }
}

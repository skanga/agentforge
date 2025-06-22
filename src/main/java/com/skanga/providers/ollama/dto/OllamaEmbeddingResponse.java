// ollama/dto/OllamaEmbeddingResponse.java
package com.skanga.providers.ollama.dto;

import java.util.List;

public record OllamaEmbeddingResponse(
    List<Double> embedding
    // Ollama might also include other fields like "model" or "created_at" in some contexts,
    // but for the /api/embeddings endpoint, the primary field is "embedding".
) {}

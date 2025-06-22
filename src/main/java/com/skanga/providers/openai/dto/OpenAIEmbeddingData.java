
// openai/dto/OpenAIEmbeddingData.java
package com.skanga.providers.openai.dto;

import java.util.List;

public record OpenAIEmbeddingData(
    String object, // e.g., "embedding"
    List<Double> embedding,
    Integer index
) {}

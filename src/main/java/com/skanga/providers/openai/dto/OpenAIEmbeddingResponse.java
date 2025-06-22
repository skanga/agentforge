
// openai/dto/OpenAIEmbeddingResponse.java
package com.skanga.providers.openai.dto;

import java.util.List;
import java.util.Map;

public record OpenAIEmbeddingResponse(
    String object, // e.g., "list"
    List<OpenAIEmbeddingData> data,
    String model,
    Map<String, Integer> usage // e.g., {"prompt_tokens": 5, "total_tokens": 5}
) {}

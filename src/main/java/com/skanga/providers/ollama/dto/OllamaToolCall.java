
// ollama/dto/OllamaToolCall.java
package com.skanga.providers.ollama.dto;

import java.util.Map;

public record OllamaToolCall(
        String id, // Not always present in Ollama's request, but good for response mapping
        String type, // Typically "function"
        OllamaFunction function
) {
    public record OllamaFunction(
            String name,
            // Arguments are expected as a JSON string by our ToolCallMessage,
            // but Ollama might send them as a map if it parses them.
            // For consistency with OpenAI DTO, let's expect a map from Ollama response
            // and convert to string when creating ToolCallMessage.FunctionCall.
            Map<String, Object> arguments
    ) {}
}

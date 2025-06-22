
// openai/dto/OpenAIStreamDelta.java
package com.skanga.providers.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record OpenAIStreamDelta(
    String content,
    String role,
    @JsonProperty("tool_calls") List<OpenAIToolCall> toolCalls
) {
    public record OpenAIToolCall(
        Integer index, // Delta tool calls might have an index
        String id,
        String type, // e.g., "function"
        Map<String, String> function // { "name": "...", "arguments": "..." } - arguments are partial
    ) {}
}


// anthropic/dto/AnthropicMessageStartData.java
package com.skanga.providers.anthropic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// data: {"type": "message_start", "message": {"id": "msg_123", "type": "message", "role": "assistant", "content": [], "model": "claude-3-opus-20240229", "stop_reason": null, "stop_sequence": null, "usage": {"input_tokens": 10, "output_tokens": 1}}}
public record AnthropicMessageStartData(
    @JsonProperty("message") MessageDetails message
) {
    public record MessageDetails(
        String id,
        String type, // "message"
        String role, // "assistant"
        AnthropicStreamMessageUsage usage
    ) {}
}

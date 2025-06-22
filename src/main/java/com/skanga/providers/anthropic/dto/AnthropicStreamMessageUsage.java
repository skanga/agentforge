
// anthropic/dto/AnthropicStreamMessageUsage.java
package com.skanga.providers.anthropic.dto;

// Represents the "usage" object within message_start or message_delta events
// "usage":{"input_tokens":10,"output_tokens":1} (message_start)
// "delta": {"usage": {"output_tokens": 12}} (message_delta)
public record AnthropicStreamMessageUsage(
    int inputTokens,
    int outputTokens
) {}

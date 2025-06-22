
// anthropic/dto/AnthropicMessageDeltaData.java
package com.skanga.providers.anthropic.dto;

// data: {"type": "message_delta", "delta": {"stop_reason": "max_tokens", "stop_sequence":null}, "usage": {"output_tokens": 102}}
// or data: {"type": "message_delta", "delta": {"usage": {"output_tokens": 12}}} (older format for usage)
public record AnthropicMessageDeltaData(
    AnthropicStreamMessageUsage usage, // This 'usage' is at the top level for message_delta, specific to final usage update
    DeltaDetails delta
) {
    public record DeltaDetails(
        AnthropicStreamMessageUsage usage,
        String stopReason
    ) {}
}

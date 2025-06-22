
// anthropic/dto/AnthropicStreamContentBlockDelta.java
package com.skanga.providers.anthropic.dto;

// Represents the "delta" object within a content_block_delta event
// data: {"type": "content_block_delta", "index": 0, "delta": {"type": "text_delta", "text": "Hello"}}
public record AnthropicStreamContentBlockDelta(
    String type, // e.g., "text_delta"
    String text  // The actual text content
) {}

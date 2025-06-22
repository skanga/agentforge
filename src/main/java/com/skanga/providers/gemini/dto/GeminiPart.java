
// gemini/dto/GeminiPart.java
package com.skanga.providers.gemini.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeminiPart(
    String text,
    InlineData inlineData,
    FileData fileData,
    GeminiFunctionCall functionCall,
    GeminiFunctionResponse functionResponse
) {
    public GeminiPart(String text) {
        this(text, null, null, null, null);
    }

    // Constructor for inline data (e.g., base64 images)
    public GeminiPart(InlineData inlineData) {
        this(null, inlineData, null, null, null);
    }

    // Constructor for function call
    public GeminiPart(GeminiFunctionCall functionCall) {
        this(null, null, null, functionCall, null);
    }

    // Constructor for function response
    public GeminiPart(GeminiFunctionResponse functionResponse) {
        this(null, null, null, null, functionResponse);
    }

    public record InlineData(
        String mimeType,
        String data // Base64 encoded string
    ) {}

    public record FileData(
            String mimeType,
            String fileUri
    ) {}
}

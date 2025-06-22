
// gemini/dto/GeminiContent.java
package com.skanga.providers.gemini.dto;

import java.util.List;

public record GeminiContent(
    // Role for input messages: "user" or "model" (for previous assistant turns)
    // Role for output candidates: always "model"
    String role,
    List<GeminiPart> parts
) {}

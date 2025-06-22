
// gemini/dto/GeminiFunctionCall.java
package com.skanga.providers.gemini.dto;

import java.util.Map;

public record GeminiFunctionCall(
    String name,
    // Gemini expects 'args' to be an object (Map), not a stringified JSON
    Map<String, Object> args
) {}

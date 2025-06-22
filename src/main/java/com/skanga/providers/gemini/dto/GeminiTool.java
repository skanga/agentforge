
// gemini/dto/GeminiTool.java
package com.skanga.providers.gemini.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// Represents the "tools" array in a Gemini request
public record GeminiTool(
    @JsonProperty("function_declarations") List<GeminiFunctionDeclaration> functionDeclarations
    // Potentially other tool types here in the future like "code_interpreter"
) {}


// gemini/dto/GeminiSystemInstruction.java
package com.skanga.providers.gemini.dto;

import java.util.List;

public record GeminiSystemInstruction(
    // While the API shows "parts", it's often simpler to just use a single text part for system instructions.
    // If complex system instructions with multiple parts are needed, this can be adapted.
    // For now, assuming it contains a single GeminiPart which is just text.
    List<GeminiPart> parts
) {
    // Convenience constructor for a simple text system instruction
    public GeminiSystemInstruction(String text) {
        this(List.of(new GeminiPart(text)));
    }
}

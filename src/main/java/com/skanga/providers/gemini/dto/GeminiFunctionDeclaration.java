
// gemini/dto/GeminiFunctionDeclaration.java
package com.skanga.providers.gemini.dto;

import java.util.Map;


// Represents a function declaration for Gemini's tool interface
public record GeminiFunctionDeclaration(
    String name,
    String description,
    // Parameters should be an OpenAPI schema object
    // For example: {"type": "OBJECT", "properties": {"param_name": {"type": "STRING"}}, "required": ["param_name"]}
    Map<String, Object> parameters
) {}

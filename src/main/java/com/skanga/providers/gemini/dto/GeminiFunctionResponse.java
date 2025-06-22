// gemini/dto/GeminiFunctionResponse.java
package com.skanga.providers.gemini.dto;

import java.util.Map;

public record GeminiFunctionResponse(
    String name, // Name of the function that was called
    // Gemini expects 'response' to be an object that would be the result of the function call
    Map<String, Object> response // Contains "name" (of function) and "content" (result)
) {
    // A common structure for the 'response' field:
    // { "name": "function_name", "content": { "key": "value", ... } }
    // Or sometimes just the result directly:
    // { "result_key": "result_value" }
    // For simplicity, we'll assume the mapper will structure this appropriately.
    // The sub-map 'response' should itself contain the actual result data.
}

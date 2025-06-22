
// deepseek/DeepseekProvider.java
package com.skanga.providers.deepseek;

import com.skanga.providers.openai.OpenAIProvider;
import java.util.Map;

/**
 * Provider for Deepseek AI models.
 * Extends OpenAIProvider as Deepseek API is OpenAI-compatible.
 */
public class DeepseekProvider extends OpenAIProvider {
    private static final String DEFAULT_BASE_URI = "https://api.deepseek.com/v1";

    public DeepseekProvider(String apiKey, String model, Map<String, Object> parameters, String baseUri) {
        super(apiKey, model, parameters, baseUri != null ? baseUri : DEFAULT_BASE_URI);
    }

    public DeepseekProvider(String apiKey, String model, Map<String, Object> parameters) {
        this(apiKey, model, parameters, null);
    }
}

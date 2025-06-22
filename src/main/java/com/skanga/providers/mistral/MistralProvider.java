
// mistral/MistralProvider.java
package com.skanga.providers.mistral;

import com.skanga.providers.openai.OpenAIProvider;
import java.util.Map;

/**
 * Provider for Mistral AI models.
 * Extends OpenAIProvider as Mistral API is OpenAI-compatible.
 */
public class MistralProvider extends OpenAIProvider {
    private static final String DEFAULT_BASE_URI = "https://api.mistral.ai/v1";

    public MistralProvider(String apiKey, String model, Map<String, Object> parameters, String baseUri) {
        super(apiKey, model, parameters, baseUri != null ? baseUri : DEFAULT_BASE_URI);
    }

    public MistralProvider(String apiKey, String model, Map<String, Object> parameters) {
        this(apiKey, model, parameters, null);
    }
}

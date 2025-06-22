package com.skanga.observability.events;

import com.skanga.chat.messages.Message;

import java.util.*;

/**
 * Event data for when an agent initiates an inference call to an AI provider (LLM).
 * This marks the beginning of a direct interaction with the underlying language model.
 *
 * @param providerName The class name or a unique identifier of the AI provider being called
 *                     (e.g., "OpenAIProvider", "AnthropicProvider"). Must not be null.
 * @param modelName    The specific model name being used for this inference call
 *                     (e.g., "gpt-3.5-turbo", "claude-2"). Must not be null.
 * @param messages     The list of {@link Message} objects forming the prompt being sent to the provider.
 *                     Must not be null. A defensive copy is made.
 * @param tools        A representation of the tools (e.g., function definitions) being passed to the provider.
 *                     This is a list of maps, where each map might represent a JSON schema of a tool.
 *                     Can be null or empty if no tools are used. A defensive copy is made.
 * @param parameters   Additional parameters sent to the provider for this specific call
 *                     (e.g., temperature, max_tokens, top_p).
 *                     Can be null or empty. A defensive copy is made.
 */
public record InferenceStart(
    String providerName,
    String modelName,
    List<Message> messages,
    List<Map<String, Object>> tools,
    Map<String, Object> parameters
) {
    /**
     * Canonical constructor for InferenceStart.
     * Ensures providerName, modelName, and messages are not null.
     * Creates defensive copies of mutable collections (messages, tools, parameters).
     */
    public InferenceStart {
        Objects.requireNonNull(providerName, "providerName cannot be null for InferenceStart.");
        Objects.requireNonNull(modelName, "modelName cannot be null for InferenceStart.");
        Objects.requireNonNull(messages, "messages list cannot be null for InferenceStart.");

        // Defensive copies for mutable collections
        messages = Collections.unmodifiableList(new ArrayList<>(messages));
        tools = (tools != null) ? Collections.unmodifiableList(new ArrayList<>(tools)) : Collections.emptyList();
        parameters = (parameters != null) ? Collections.unmodifiableMap(new HashMap<>(parameters)) : Collections.emptyMap();
    }

    /**
     * Convenience constructor without tools or specific parameters.
     * @param providerName Name of the provider.
     * @param modelName Name of the model.
     * @param messages List of messages for the prompt.
     */
    public InferenceStart(String providerName, String modelName, List<Message> messages) {
        this(providerName, modelName, messages, null, null);
    }
}

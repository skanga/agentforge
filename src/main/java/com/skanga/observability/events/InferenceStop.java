package com.skanga.observability.events;

import com.skanga.chat.messages.Message;
import java.util.Objects;

/**
 * Event data for when an agent's inference call to an AI provider completes.
 * This event signifies the end of a direct interaction with the language model
 * that was initiated by an {@link InferenceStart} event.
 *
 * @param providerName The class name or identifier of the AI provider that was called. Must not be null.
 * @param modelName    The specific model that was used. Must not be null.
 * @param response     The {@link Message} response received from the provider. This message
 *                     should ideally contain {@link com.skanga.core.Usage} information if the
 *                     provider returns it. Can be null if the inference call resulted in an
 *                     error before a response message object could be formed.
 * @param durationMs   The duration of the inference call in milliseconds, from when the request
 *                     was initiated to when the response was received.
 * @param requestContext Optional: The {@link InferenceStart} event object or another form of context
 *                       that allows correlation with the corresponding inference start. Can be null.
 *                       This helps in linking start and stop events in observability systems if they
 *                       are processed separately or if span management relies on such correlation.
 */
public record InferenceStop(
    String providerName,
    String modelName,
    Message response,    // Can be null if error occurred during inference itself
    long durationMs,
    InferenceStart requestContext
) {
    /**
     * Canonical constructor for InferenceStop.
     * Ensures providerName and modelName are not null.
     */
    public InferenceStop {
        Objects.requireNonNull(providerName, "providerName cannot be null for InferenceStop.");
        Objects.requireNonNull(modelName, "modelName cannot be null for InferenceStop.");
        if (durationMs < 0) {
            // Or log a warning, as duration might be unknown if start time wasn't captured.
            // For now, allow -1 or similar if duration is truly unknown, but typically it should be positive.
            // throw new IllegalArgumentException("Duration cannot be negative.");
        }
    }

    /**
     * Convenience constructor without explicit request context.
     * @param providerName Name of the provider.
     * @param modelName Name of the model.
     * @param response The response message from the provider.
     * @param durationMs Duration of the inference call.
     */
     public InferenceStop(String providerName, String modelName, Message response, long durationMs) {
        this(providerName, modelName, response, durationMs, null);
    }
}

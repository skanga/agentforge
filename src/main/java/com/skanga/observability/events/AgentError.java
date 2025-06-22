package com.skanga.observability.events;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an error event that occurred within an agent or related component.
 * This event is typically used to signal exceptions or failures during processing.
 *
 * @param exception The {@link Throwable} instance representing the error that occurred. Must not be null.
 * @param critical  A boolean flag indicating if the error is considered critical.
 *                  Critical errors might signify a major failure in an agent's operation,
 *                  while non-critical errors might be recoverable or less impactful.
 *                  This can be used by observers (e.g., loggers, monitors) to determine
 *                  the severity of reporting (e.g., log level ERROR vs. WARN).
 * @param message   An optional additional human-readable message providing more context about the error.
 *                  Can be null.
 * @param context   An optional map of key-value pairs providing further contextual information
 *                  about the state of the agent or component when the error occurred. Can be null.
 *                  Keys should be descriptive strings. Values can be any object, but will
 *                  typically be serialized to strings for observability systems.
 */
public record AgentError(
    Throwable exception,
    boolean critical,
    String message,
    Map<String, Object> context
) {
    /**
     * Canonical constructor for AgentError.
     * Ensures the exception is not null and provides unmodifiable context if given.
     */
    public AgentError {
        Objects.requireNonNull(exception, "Exception cannot be null for AgentError.");
        context = (context != null) ? Collections.unmodifiableMap(context) : Collections.emptyMap();
    }

    /**
     * Convenience constructor for critical errors without an additional message or context.
     * @param exception The throwable.
     * @param critical  True if critical, false otherwise.
     */
    public AgentError(Throwable exception, boolean critical) {
        this(exception, critical, null, null);
    }

    /**
     * Convenience constructor for errors with a message but no extra context.
     * @param exception The throwable.
     * @param critical  True if critical, false otherwise.
     * @param message   Additional error message.
     */
    public AgentError(Throwable exception, boolean critical, String message) {
        this(exception, critical, message, null);
    }
}

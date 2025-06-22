
package com.skanga.core.exceptions;

/**
 * A runtime exception related to Agent operations.
 * This serves as a specialized exception for agent-specific errors.
 */
public class AgentException extends BaseAiException {

    /**
     * Constructs a new agent exception with the specified detail message.
     * @param message the detail message.
     */
    public AgentException(String message) {
        super(message, ErrorCategory.PROCESSING);
    }

    /**
     * Constructs a new agent exception with the specified detail message and cause.
     * @param message the detail message.
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
     *              A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.
     */
    public AgentException(String message, Throwable cause) {
        super(message, cause, ErrorCategory.PROCESSING);
    }

    /**
     * Constructs a new agent exception with the specified detail message and category.
     * @param message the detail message.
     * @param category the error category.
     */
    public AgentException(String message, ErrorCategory category) {
        super(message, category);
    }

    /**
     * Constructs a new agent exception with the specified detail message, cause, and category.
     * @param message the detail message.
     * @param cause the cause.
     * @param category the error category.
     */
    public AgentException(String message, Throwable cause, ErrorCategory category) {
        super(message, cause, category);
    }
}

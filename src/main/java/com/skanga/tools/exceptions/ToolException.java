package com.skanga.tools.exceptions;

/**
 * Base runtime exception for errors related to {@link com.skanga.tools.Tool} definition,
 * validation, or execution.
 * This serves as a common superclass for more specific tool-related exceptions like
 * {@link MissingToolParameterException} or {@link ToolCallableException}.
 */
public class ToolException extends RuntimeException {

    /**
     * Constructs a new tool exception with the specified detail message.
     * @param message the detail message.
     */
    public ToolException(String message) {
        super(message);
    }

    /**
     * Constructs a new tool exception with the specified detail message and cause.
     * @param message the detail message.
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
     *              A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.
     */
    public ToolException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.skanga.tools.exceptions;

/**
 * Exception thrown when there's an issue with a tool's {@link com.skanga.tools.ToolCallable} logic.
 * This can occur if a callable is not set before execution, or if the callable
 * itself throws an exception during its {@code execute} method.
 */
public class ToolCallableException extends ToolException {

    /**
     * Constructs a new ToolCallableException with the specified detail message.
     * @param message the detail message.
     */
    public ToolCallableException(String message) {
        super(message);
    }

    /**
     * Constructs a new ToolCallableException with the specified detail message and cause.
     * @param message the detail message.
     * @param cause the underlying cause of the execution failure.
     */
    public ToolCallableException(String message, Throwable cause) {
        super(message, cause);
    }
}

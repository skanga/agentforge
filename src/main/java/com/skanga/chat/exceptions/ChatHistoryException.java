package com.skanga.chat.exceptions;

/**
 * A runtime exception related to Chat History operations.
 * This can be thrown for issues such as failing to load, save, or parse chat history data.
 *
 * It extends {@link RuntimeException} making it an unchecked exception.
 * Consider extending a checked `Exception` if callers should be forced to handle these explicitly.
 */
public class ChatHistoryException extends RuntimeException { // Or extends AgentException

    /**
     * Constructs a new chat history exception with the specified detail message.
     * @param message the detail message.
     */
    public ChatHistoryException(String message) {
        super(message);
    }

    /**
     * Constructs a new chat history exception with the specified detail message and cause.
     * @param message the detail message.
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
     *              A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.
     */
    public ChatHistoryException(String message, Throwable cause) {
        super(message, cause);
    }
}

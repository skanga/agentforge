
package com.skanga.rag.vectorstore;

import com.skanga.core.exceptions.BaseAiException;

/**
 * Custom runtime exception for errors related to {@link VectorStore} operations.
 * This can be thrown for issues such as:
 * <ul>
 *   <li>Failures during document addition, update, or deletion.</li>
 *   <li>Errors during similarity search execution.</li>
 *   <li>Configuration problems (e.g., connection issues with a remote vector database).</li>
 *   <li>I/O errors for file-based vector stores.</li>
 *   <li>Data integrity issues (e.g., documents missing embeddings when required).</li>
 * </ul>
 */
public class VectorStoreException extends BaseAiException {

    /**
     * Constructs a new vector store exception with the specified detail message.
     * @param message the detail message.
     */
    public VectorStoreException(String message) {
        super(message, ErrorCategory.EXTERNAL_SERVICE);
    }

    /**
     * Constructs a new vector store exception with the specified detail message and cause.
     * @param message the detail message.
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
     *              A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.
     */
    public VectorStoreException(String message, Throwable cause) {
        super(message, cause, ErrorCategory.EXTERNAL_SERVICE);
    }

    /**
     * Constructs a new vector store exception with a detail message, HTTP status code,
     * and the raw error body from an external service.
     * The error body is sanitized for inclusion in the main exception message.
     *
     * @param message           The detail message for the exception.
     * @param statusCode        The HTTP status code received.
     * @param errorBody         The raw error body string from the HTTP response.
     */
    public VectorStoreException(String message, int statusCode, String errorBody) {
        super(message, statusCode, errorBody);
    }

    /**
     * Constructs a new vector store exception with a detail message, cause, HTTP status code,
     * and the raw error body from an external service.
     * The error body is sanitized for inclusion in the main exception message.
     *
     * @param message           The detail message for the exception.
     * @param cause             The underlying cause of this exception.
     * @param statusCode        The HTTP status code received.
     * @param errorBody         The raw error body string from the HTTP response.
     */
    public VectorStoreException(String message, Throwable cause, int statusCode, String errorBody) {
        super(message, cause, statusCode, errorBody, ErrorCategory.EXTERNAL_SERVICE);
    }
}


package com.skanga.rag.postprocessing;

import com.skanga.core.exceptions.BaseAiException;

/**
 * Custom runtime exception for errors that occur during the document post-processing phase
 * in a RAG pipeline. This can be thrown by {@link PostProcessor} implementations.
 *
 * <p>It includes optional fields for HTTP status code and raw error body if the
 * post-processor interacts with an external API (e.g., a reranking service).</p>
 *
 * <p>Consider making this extend a more general `BaseAiException` if such a base
 * exception exists for the library.</p>
 */
public class PostProcessorException extends BaseAiException {

    /**
     * Constructs a new post-processor exception with the specified detail message.
     * @param message the detail message.
     */
    public PostProcessorException(String message) {
        super(message, ErrorCategory.PROCESSING);
    }

    /**
     * Constructs a new post-processor exception with the specified detail message and cause.
     * @param message the detail message.
     * @param cause the underlying cause of this exception.
     */
    public PostProcessorException(String message, Throwable cause) {
        super(message, cause, ErrorCategory.PROCESSING);
    }

    /**
     * Constructs a new post-processor exception with a detail message, HTTP status code,
     * and the raw error body from an external service.
     * The error body is sanitized for inclusion in the main exception message.
     *
     * @param message           The detail message for the exception.
     * @param statusCode        The HTTP status code received.
     * @param errorBody         The raw error body string from the HTTP response.
     */
    public PostProcessorException(String message, int statusCode, String errorBody) {
        super(message, statusCode, errorBody);
    }

    /**
     * Constructs a new post-processor exception with a detail message, cause, HTTP status code,
     * and the raw error body from an external service.
     * The error body is sanitized for inclusion in the main exception message.
     *
     * @param message           The detail message for the exception.
     * @param cause             The underlying cause of this exception.
     * @param statusCode        The HTTP status code received.
     * @param errorBody         The raw error body string from the HTTP response.
     */
    public PostProcessorException(String message, Throwable cause, int statusCode, String errorBody) {
        super(message, cause, statusCode, errorBody, ErrorCategory.PROCESSING);
    }
}

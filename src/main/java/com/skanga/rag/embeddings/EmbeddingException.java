package com.skanga.rag.embeddings;

/**
 * Custom runtime exception for errors related to embedding generation or processing.
 * This can be thrown by {@link EmbeddingProvider} implementations when they encounter
 * issues such as API errors, configuration problems, or invalid input for embeddings.
 *
 * <p>It includes optional fields for HTTP status code and raw error body from an API
 * to aid in diagnosing provider-specific issues.</p>
 */
public class EmbeddingException extends RuntimeException {

    /** HTTP status code from the provider, if the error is HTTP related. -1 if not applicable. */
    private int statusCode = -1;
    /** Raw error body from the provider's HTTP response, if available and relevant. */
    private String errorBody;

    /**
     * Constructs a new embedding exception with the specified detail message.
     * @param message the detail message.
     */
    public EmbeddingException(String message) {
        super(message);
    }

    /**
     * Constructs a new embedding exception with the specified detail message and cause.
     * @param message the detail message.
     * @param cause the underlying cause of this exception.
     */
    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new embedding exception with a detail message, HTTP status code,
     * and the raw error body from the provider.
     * The error body is sanitized for inclusion in the main exception message.
     *
     * @param message           The detail message for the exception.
     * @param statusCode        The HTTP status code received from the provider.
     * @param errorBody         The raw error body string from the HTTP response.
     */
    public EmbeddingException(String message, int statusCode, String errorBody) {
        super(message + " (Status: " + statusCode + (errorBody != null ? ", Body: " + sanitizeErrorBodyForMessage(errorBody) : "") + ")");
        this.statusCode = statusCode;
        this.errorBody = errorBody; // Store raw error body
    }

    /**
     * Constructs a new embedding exception with a detail message, cause, HTTP status code,
     * and the raw error body from the provider.
     * The error body is sanitized for inclusion in the main exception message.
     *
     * @param message           The detail message for the exception.
     * @param cause             The underlying cause of this exception.
     * @param statusCode        The HTTP status code received from the provider.
     * @param errorBody         The raw error body string from the HTTP response.
     */
    public EmbeddingException(String message, Throwable cause, int statusCode, String errorBody) {
        super(message + " (Status: " + statusCode + (errorBody != null ? ", Body: " + sanitizeErrorBodyForMessage(errorBody) : "") + ")", cause);
        this.statusCode = statusCode;
        this.errorBody = errorBody; // Store raw error body
    }

    /**
     * Gets the HTTP status code associated with this exception, if applicable.
     * @return The HTTP status code, or -1 if not set or not applicable.
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Gets the raw error body received from the provider, if available.
     * This can be useful for detailed debugging of provider-specific error messages.
     * @return The raw error body string, or null if not set or not applicable.
     */
    public String getErrorBody() {
        return errorBody;
    }

    /**
     * Sanitizes an error body string for inclusion in the main exception message.
     * Truncates to a reasonable length and escapes newlines.
     * @param body The raw error body.
     * @return A sanitized and truncated string, or null if input is null.
     */
    private static String sanitizeErrorBodyForMessage(String body) {
        if (body == null) return null;
        // Prevent extremely long error bodies from making the main exception message unreadable.
        String sanitized = body.replace("\n", "\\n").replace("\r", "\\r");
        return sanitized.substring(0, Math.min(sanitized.length(), 256)) + (sanitized.length() > 256 ? "..." : "");
    }
}

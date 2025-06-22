
package com.skanga.core.exceptions;

/**
 * Base runtime exception for all AI-related operations in the library.
 * Provides common functionality for HTTP status codes, error bodies, and error categorization.
 *
 * <p>This serves as the root exception type for:</p>
 * <ul>
 *   <li>Provider interactions (API calls, authentication, parsing)</li>
 *   <li>Agent operations (chat, streaming, structured output)</li>
 *   <li>Vector store operations (document storage, similarity search)</li>
 *   <li>Post-processing operations (reranking, filtering)</li>
 *   <li>Tool execution and management</li>
 * </ul>
 */
public class BaseAiException extends RuntimeException {

    /** HTTP status code from external service, if applicable. -1 if not set. */
    private int statusCode = -1;
    /** Raw error body from external service's HTTP response, if available. */
    private String errorBody;
    /** Category of the error for better error handling and logging. */
    private ErrorCategory category = ErrorCategory.UNKNOWN;

    /**
     * Categories of AI-related errors for better classification and handling.
     */
    public enum ErrorCategory {
        /** Network or I/O related errors */
        NETWORK,
        /** Authentication or authorization failures */
        AUTHENTICATION,
        /** Invalid requests or parameters */
        VALIDATION,
        /** External service errors (provider APIs, vector stores, etc.) */
        EXTERNAL_SERVICE,
        /** Internal processing errors (parsing, serialization, etc.) */
        PROCESSING,
        /** Resource limitations (rate limits, quotas, etc.) */
        RESOURCE_LIMIT,
        /** Configuration or setup issues */
        CONFIGURATION,
        /** Unknown or unclassified errors */
        UNKNOWN
    }

    /**
     * Constructs a new AI exception with the specified detail message.
     * @param message the detail message.
     */
    public BaseAiException(String message) {
        super(message);
    }

    /**
     * Constructs a new AI exception with the specified detail message and category.
     * @param message the detail message.
     * @param category the error category.
     */
    public BaseAiException(String message, ErrorCategory category) {
        super(message);
        this.category = category;
    }

    /**
     * Constructs a new AI exception with the specified detail message and cause.
     * @param message the detail message.
     * @param cause the cause.
     */
    public BaseAiException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new AI exception with the specified detail message, cause, and category.
     * @param message the detail message.
     * @param cause the cause.
     * @param category the error category.
     */
    public BaseAiException(String message, Throwable cause, ErrorCategory category) {
        super(message, cause);
        this.category = category;
    }

    /**
     * Constructs a new AI exception with HTTP status and error body information.
     * @param message the detail message.
     * @param statusCode the HTTP status code.
     * @param errorBody the raw error body.
     */
    public BaseAiException(String message, int statusCode, String errorBody) {
        super(message + " (Status: " + statusCode + (errorBody != null ? ", Body: " + sanitizeErrorBody(errorBody) : "") + ")");
        this.statusCode = statusCode;
        this.errorBody = errorBody;
        this.category = categorizeFromStatusCode(statusCode);
    }

    /**
     * Constructs a new AI exception with full context information.
     * @param message the detail message.
     * @param cause the cause.
     * @param statusCode the HTTP status code.
     * @param errorBody the raw error body.
     * @param category the error category.
     */
    public BaseAiException(String message, Throwable cause, int statusCode, String errorBody, ErrorCategory category) {
        super(message + " (Status: " + statusCode + (errorBody != null ? ", Body: " + sanitizeErrorBody(errorBody) : "") + ")", cause);
        this.statusCode = statusCode;
        this.errorBody = errorBody;
        this.category = category;
    }

    /**
     * Gets the HTTP status code associated with this exception, if applicable.
     * @return The HTTP status code, or -1 if not set.
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Gets the raw error body received from an external service, if available.
     * @return The raw error body string, or null if not set.
     */
    public String getErrorBody() {
        return errorBody;
    }

    /**
     * Gets the error category for this exception.
     * @return The error category.
     */
    public ErrorCategory getCategory() {
        return category;
    }

    /**
     * Checks if this is a retryable error based on the category and status code.
     * @return true if the error might be retryable.
     */
    public boolean isRetryable() {
        switch (category) {
            case NETWORK:
                return true;
            case EXTERNAL_SERVICE:
                // 5xx errors are typically retryable, 4xx are not
                return statusCode >= 500 && statusCode < 600;
            case RESOURCE_LIMIT:
                // Rate limits might be retryable after waiting
                return statusCode == 429;
            default:
                return false;
        }
    }

    /**
     * Checks if this is likely a transient error that might resolve itself.
     * @return true if the error is likely transient.
     */
    public boolean isTransient() {
        return category == ErrorCategory.NETWORK ||
                (category == ErrorCategory.EXTERNAL_SERVICE && statusCode >= 500) ||
                statusCode == 429 || statusCode == 503;
    }

    /**
     * Sanitizes an error body string for inclusion in the main exception message.
     * @param body The raw error body.
     * @return A sanitized and truncated string, or null if input is null.
     */
    protected static String sanitizeErrorBody(String body) {
        if (body == null) return null;
        String sanitized = body.replace("\n", "\\n").replace("\r", "\\r");
        return sanitized.substring(0, Math.min(sanitized.length(), 256)) + (sanitized.length() > 256 ? "..." : "");
    }

    /**
     * Attempts to categorize an error based on HTTP status code.
     * @param statusCode The HTTP status code.
     * @return The most appropriate error category.
     */
    private static ErrorCategory categorizeFromStatusCode(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return ErrorCategory.AUTHENTICATION;
        } else if (statusCode == 400 || statusCode == 422) {
            return ErrorCategory.VALIDATION;
        } else if (statusCode == 429) {
            return ErrorCategory.RESOURCE_LIMIT;
        } else if (statusCode >= 500) {
            return ErrorCategory.EXTERNAL_SERVICE;
        } else if (statusCode >= 400) {
            return ErrorCategory.EXTERNAL_SERVICE;
        } else {
            return ErrorCategory.UNKNOWN;
        }
    }
}

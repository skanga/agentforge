
package com.skanga.core.exceptions;

/**
 * A runtime exception related to AI Provider operations.
 * This is typically thrown when there's an error communicating with the
 * provider's API (e.g., network issues, authentication failures, API errors)
 * or when parsing responses from the provider.
 */
public class ProviderException extends BaseAiException {

    /**
     * Constructs a new provider exception with the specified detail message.
     * @param message the detail message.
     */
    public ProviderException(String message) {
        super(message, ErrorCategory.EXTERNAL_SERVICE);
    }

    /**
     * Constructs a new provider exception with the specified detail message and cause.
     * @param message the detail message.
     * @param cause the cause.
     */
    public ProviderException(String message, Throwable cause) {
        super(message, cause, ErrorCategory.EXTERNAL_SERVICE);
    }

    /**
     * Constructs a new provider exception with a detail message, HTTP status code,
     * and the raw error body from the provider.
     *
     * @param message           The detail message for the exception.
     * @param statusCode        The HTTP status code received from the provider.
     * @param providerErrorBody The raw error body string from the HTTP response.
     */
    public ProviderException(String message, int statusCode, String providerErrorBody) {
        super(message, statusCode, providerErrorBody);
    }

    /**
     * Constructs a new provider exception with a detail message, cause, HTTP status code,
     * and the raw error body from the provider.
     *
     * @param message           The detail message for the exception.
     * @param cause             The underlying cause of this exception.
     * @param statusCode        The HTTP status code received from the provider.
     * @param providerErrorBody The raw error body string from the HTTP response.
     */
    public ProviderException(String message, Throwable cause, int statusCode, String providerErrorBody) {
        super(message, cause, statusCode, providerErrorBody, ErrorCategory.EXTERNAL_SERVICE);
    }

    /**
     * Gets the raw error body received from the provider, if available.
     * This can be useful for debugging provider-specific error messages.
     * @return The raw error body string, or null if not set.
     */
    public String getProviderErrorBody() {
        return getErrorBody();
    }
/*
    private static String sanitizeErrorBody(String errorBody) {
        if (errorBody == null) return null;
        // Prevent extremely long error bodies from making the exception message unreadable.
        // Also, replace newlines for better single-line logging of the main message.
        String sanitized = errorBody.replace("\n", "\\n").replace("\r", "\\r");
        return sanitized.substring(0, Math.min(sanitized.length(), 512)) + (sanitized.length() > 512 ? "..." : "");
    }
    */
}

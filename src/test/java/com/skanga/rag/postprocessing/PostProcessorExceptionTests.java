package com.skanga.rag.postprocessing;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PostProcessorExceptionTests {

    @Test
    void constructor_withMessage_setsMessageAndDefaults() {
        String errorMessage = "Test post-processor error";
        PostProcessorException exception = new PostProcessorException(errorMessage);

        assertTrue(exception.getMessage().contains(errorMessage)); // Default constructor might append status/body if null
        assertNull(exception.getCause());
        assertEquals(-1, exception.getStatusCode());
        assertNull(exception.getErrorBody());
    }

    @Test
    void constructor_withMessageAndCause_setsAll() {
        String errorMessage = "Test post-processor error with cause";
        Throwable cause = new RuntimeException("Underlying issue");
        PostProcessorException exception = new PostProcessorException(errorMessage, cause);

        assertEquals(errorMessage, exception.getMessage());
        assertSame(cause, exception.getCause());
        assertEquals(-1, exception.getStatusCode());
        assertNull(exception.getErrorBody());
    }

    @Test
    void constructor_withMessageStatusCodeAndBody_setsAllAndFormatsMessage() {
        String errorMessage = "API error during post-processing";
        int statusCode = 429;
        String errorBody = "{\"error\":\"Rate limit exceeded\"}";
        PostProcessorException exception = new PostProcessorException(errorMessage, statusCode, errorBody);

        assertTrue(exception.getMessage().startsWith(errorMessage));
        assertTrue(exception.getMessage().contains("(Status: 429"));
        assertTrue(exception.getMessage().contains("Body: {\"error\":\"Rate limit exceeded\"}"));
        assertNull(exception.getCause());
        assertEquals(statusCode, exception.getStatusCode());
        assertEquals(errorBody, exception.getErrorBody());
    }

    @Test
    void constructor_withMessageCauseStatusCodeAndBody_setsAllAndFormatsMessage() {
        String errorMessage = "API error during post-processing with cause";
        Throwable cause = new IOException("Network issue");
        int statusCode = 503;
        String errorBody = "Service Unavailable";
        PostProcessorException exception = new PostProcessorException(errorMessage, cause, statusCode, errorBody);

        assertTrue(exception.getMessage().startsWith(errorMessage));
        assertTrue(exception.getMessage().contains("(Status: 503"));
        assertTrue(exception.getMessage().contains("Body: Service Unavailable"));
        assertSame(cause, exception.getCause());
        assertEquals(statusCode, exception.getStatusCode());
        assertEquals(errorBody, exception.getErrorBody());
    }

    @Test
    void constructor_withStatusCodeAndNullBody_formatsMessageWithoutBody() {
        String errorMessage = "API error, no body";
        int statusCode = 404;
        PostProcessorException exception = new PostProcessorException(errorMessage, statusCode, null);

        assertTrue(exception.getMessage().startsWith(errorMessage));
        assertTrue(exception.getMessage().contains("(Status: 404)"));
        assertFalse(exception.getMessage().contains("Body:"));
        assertEquals(statusCode, exception.getStatusCode());
        assertNull(exception.getErrorBody());
    }
}

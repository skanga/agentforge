package com.skanga.rag.dataloader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class DocumentLoaderExceptionTests {

    @Test
    void constructor_withMessage_setsMessage() {
        String errorMessage = "Test document loader error";
        DocumentLoaderException exception = new DocumentLoaderException(errorMessage);
        assertEquals(errorMessage, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void constructor_withMessageAndCause_setsMessageAndCause() {
        String errorMessage = "Test document loader error with cause";
        Throwable cause = new IOException("Underlying IO issue");
        DocumentLoaderException exception = new DocumentLoaderException(errorMessage, cause);

        assertEquals(errorMessage, exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}

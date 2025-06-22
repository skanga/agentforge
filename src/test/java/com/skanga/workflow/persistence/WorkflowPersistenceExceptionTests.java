package com.skanga.workflow.persistence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowPersistenceExceptionTests {

    @Test
    void constructor_withMessage_setsMessage() {
        String errorMessage = "Test persistence error";
        WorkflowPersistenceException exception = new WorkflowPersistenceException(errorMessage);
        assertEquals(errorMessage, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void constructor_withMessageAndCause_setsMessageAndCause() {
        String errorMessage = "Test persistence error with cause";
        Throwable cause = new RuntimeException("Underlying DB issue");
        WorkflowPersistenceException exception = new WorkflowPersistenceException(errorMessage, cause);

        assertEquals(errorMessage, exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}

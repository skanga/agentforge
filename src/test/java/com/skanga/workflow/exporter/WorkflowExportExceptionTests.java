package com.skanga.workflow.exporter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowExportExceptionTests {

    @Test
    void constructor_withMessage_setsMessage() {
        String errorMessage = "Test export error";
        WorkflowExportException exception = new WorkflowExportException(errorMessage);
        assertEquals(errorMessage, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void constructor_withMessageAndCause_setsMessageAndCause() {
        String errorMessage = "Test export error with cause";
        Throwable cause = new RuntimeException("Underlying formatting issue");
        WorkflowExportException exception = new WorkflowExportException(errorMessage, cause);

        assertEquals(errorMessage, exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}

package com.skanga.workflow.exporter;

import com.skanga.workflow.exception.WorkflowException;

/**
 * Custom exception for errors that occur during the export of a
 * {@link com.skanga.workflow.Workflow} definition to a specific format
 * (e.g., Mermaid, JSON, XML).
 *
 * <p>This exception is typically thrown by implementations of the {@link WorkflowExporter} interface.</p>
 * It extends {@link WorkflowException}, making it part of the workflow system's exception hierarchy.
 */
public class WorkflowExportException extends WorkflowException {

    /**
     * Constructs a new workflow export exception with the specified detail message.
     * @param message the detail message.
     */
    public WorkflowExportException(String message) {
        super(message);
    }

    /**
     * Constructs a new workflow export exception with the specified detail message and cause.
     * @param message the detail message.
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
     */
    public WorkflowExportException(String message, Throwable cause) {
        super(message, cause);
    }
}

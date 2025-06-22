package com.skanga.workflow.persistence;

import com.skanga.workflow.exception.WorkflowException;

/**
 * Custom exception for errors related to workflow state persistence operations.
 * This exception is typically thrown by implementations of the {@link WorkflowPersistence}
 * interface when they encounter issues saving, loading, or deleting workflow states.
 *
 * <p>It extends {@link WorkflowException}, making it part of the workflow system's
 * hierarchy of exceptions.</p>
 */
public class WorkflowPersistenceException extends WorkflowException {

    /**
     * Constructs a new workflow persistence exception with the specified detail message.
     * @param message the detail message.
     */
    public WorkflowPersistenceException(String message) {
        super(message);
    }

    /**
     * Constructs a new workflow persistence exception with the specified detail message and cause.
     * @param message the detail message.
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
     *              A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.
     */
    public WorkflowPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}

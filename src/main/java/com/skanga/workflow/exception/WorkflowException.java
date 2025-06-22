package com.skanga.workflow.exception;

/**
 * Base runtime exception for errors that occur within the workflow engine or
 * during the execution of a workflow or its components (nodes, edges).
 *
 * <p>This serves as a common superclass for more specific workflow-related exceptions,
 * such as {@link WorkflowInterrupt}, {@link com.skanga.workflow.persistence.WorkflowPersistenceException},
 * or exceptions arising from node execution failures.</p>
 */
public class WorkflowException extends RuntimeException {

    /**
     * Constructs a new workflow exception with the specified detail message.
     * @param message the detail message.
     */
    public WorkflowException(String message) {
        super(message);
    }

    /**
     * Constructs a new workflow exception with the specified detail message and cause.
     * @param message the detail message.
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
     *              A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.
     */
    public WorkflowException(String message, Throwable cause) {
        super(message, cause);
    }
}

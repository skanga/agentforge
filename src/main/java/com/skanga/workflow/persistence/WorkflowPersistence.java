package com.skanga.workflow.persistence;

import com.skanga.workflow.exception.WorkflowInterrupt;

/**
 * Interface for workflow state persistence mechanisms.
 * Implementations of this interface are responsible for saving the state of
 * interrupted workflows and loading them for resumption.
 *
 * <p>This allows the {@link com.skanga.workflow.Workflow} engine to be decoupled
 * from the specific storage strategy (e.g., in-memory, file system, database).</p>
 *
 * <p>When a workflow is interrupted (e.g., a node throws a {@link WorkflowInterrupt}),
 * the engine will call the {@link #save(String, WorkflowInterrupt)} method.
 * When a workflow is to be resumed, the engine will call {@link #load(String)}.</p>
 */
public interface WorkflowPersistence {

    /**
     * Saves the state of an interrupted workflow.
     * The {@link WorkflowInterrupt} object contains all necessary information
     * to resume the workflow later, including the ID of the node where the interruption
     * occurred, the complete workflow state at that point, and any specific data
     * the interrupting node wished to save.
     *
     * @param workflowId The unique ID of the workflow instance being saved. This ID
     *                   will be used later to load and resume the workflow.
     * @param interrupt  The {@link WorkflowInterrupt} object containing the state to save.
     *                   This includes the node ID where interruption occurred and the workflow state.
     * @throws WorkflowPersistenceException if the saving operation fails (e.g., I/O error,
     *                                      database error, serialization error).
     */
    void save(String workflowId, WorkflowInterrupt interrupt) throws WorkflowPersistenceException;

    /**
     * Loads the state of a previously interrupted and saved workflow.
     *
     * @param workflowId The unique ID of the workflow instance to load.
     * @return The {@link WorkflowInterrupt} object that was previously saved. This object
     *         contains the workflow state, the ID of the node from which to resume,
     *         and any data saved at the point of interruption.
     *         Returns {@code null} if no saved state is found for the given {@code workflowId},
     *         indicating the workflow might not have been interrupted or was already completed
     *         and its state deleted.
     * @throws WorkflowPersistenceException if the loading operation fails (e.g., I/O error,
     *                                      database error, deserialization error).
     */
    WorkflowInterrupt load(String workflowId) throws WorkflowPersistenceException;

    /**
     * Deletes the persisted state of a workflow.
     * This is typically called after a workflow has successfully completed its execution
     * (including after a resume) or if the workflow is explicitly cancelled and its
     * persisted state is no longer needed.
     *
     * @param workflowId The unique ID of the workflow instance whose persisted state should be deleted.
     * @throws WorkflowPersistenceException if the deletion operation fails.
     */
    void delete(String workflowId) throws WorkflowPersistenceException;
}

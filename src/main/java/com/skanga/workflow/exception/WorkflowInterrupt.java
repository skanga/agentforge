package com.skanga.workflow.exception;

import com.skanga.workflow.WorkflowState;

import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Objects;

/**
 * A specialized {@link WorkflowException} that signals a controlled interruption
 * of a workflow's execution. This is typically thrown by a {@link com.skanga.workflow.graph.Node}
 * when it needs to pause execution, for example, to wait for external input like human feedback.
 *
 * <p>When a {@code WorkflowInterrupt} is thrown, the {@link com.skanga.workflow.Workflow}
 * engine is expected to:
 * <ol>
 *   <li>Catch this exception.</li>
 *   <li>Persist the current {@link #getWorkflowState()} and other interrupt details
 *       (like {@link #getCurrentNodeId()} and {@link #getDataToSave()}) using a
 *       {@link com.skanga.workflow.persistence.WorkflowPersistence} layer.</li>
 *   <li>Halt further execution of the workflow.</li>
 * </ol>
 * The workflow can then be resumed later using {@link com.skanga.workflow.Workflow#resume(Object)},
 * which will load the saved state and provide any human feedback to the interrupted node.
 * </p>
 *
 * @see com.skanga.workflow.WorkflowContext#interrupt(Map)
 */
public class WorkflowInterrupt extends WorkflowException {

    /**
     * Optional data that the interrupting node wishes to explicitly save
     * as part of the workflow's state at the point of interruption.
     * This data will be merged into the {@code workflowState} before persistence.
     */
    private final Map<String, Object> dataToSave;
    /** The ID of the node that initiated the interruption. */
    private final String currentNodeId;
    /**
     * The complete state of the workflow at the moment of interruption.
     * This state (potentially merged with {@code dataToSave}) is what gets persisted.
     */
    private final WorkflowState workflowState;

    /**
     * Constructs a WorkflowInterrupt.
     *
     * @param message        A message describing the reason for interruption.
     * @param dataToSave     Optional data to be merged with the workflow state before saving. Can be null.
     * @param currentNodeId  The ID of the node where the interruption occurred. Must not be null.
     * @param workflowState  The current {@link WorkflowState} at the time of interruption. Must not be null.
     *                       A defensive copy should ideally be passed if the original state object is mutable
     *                       and might change after the interrupt is thrown but before it's fully handled/persisted.
     *                       The {@link com.skanga.workflow.WorkflowContext#interrupt(Map)} method handles creating a copy.
     */
    public WorkflowInterrupt(String message, Map<String, Object> dataToSave, String currentNodeId, WorkflowState workflowState) {
        super(message);
        this.dataToSave = (dataToSave != null) ? Collections.unmodifiableMap(new HashMap<>(dataToSave)) : Collections.emptyMap();
        this.currentNodeId = Objects.requireNonNull(currentNodeId, "Current node ID cannot be null for WorkflowInterrupt.");
        // Store the state provided. If it's a copy, that's good.
        this.workflowState = Objects.requireNonNull(workflowState, "WorkflowState cannot be null for WorkflowInterrupt.");
    }

    /**
     * Constructs a WorkflowInterrupt with a default message.
     *
     * @param dataToSave     Optional data to be merged with the workflow state.
     * @param currentNodeId  The ID of the node where the interruption occurred.
     * @param workflowState  The current {@link WorkflowState}.
     */
    public WorkflowInterrupt(Map<String, Object> dataToSave, String currentNodeId, WorkflowState workflowState) {
        this("Workflow execution interrupted at node: " + currentNodeId + ". Current state and data have been prepared for saving.",
             dataToSave, currentNodeId, workflowState);
    }

    /**
     * Gets the data that the interrupting node requested to be saved.
     * This map is unmodifiable.
     * @return A map of data to save, or an empty map if null was provided.
     */
    public Map<String, Object> getDataToSave() {
        return dataToSave;
    }

    /**
     * Gets the ID of the node that initiated the interruption.
     * @return The node ID.
     */
    public String getCurrentNodeId() {
        return currentNodeId;
    }

    /**
     * Gets the state of the workflow at the time of interruption.
     * This is the state that should be persisted to allow for resumption.
     * It's crucial that this state object accurately reflects the workflow's progress
     * up to the point of interruption, potentially including any data from {@link #getDataToSave()}.
     * @return The {@link WorkflowState}.
     */
    public WorkflowState getWorkflowState() {
        return workflowState;
    }
}

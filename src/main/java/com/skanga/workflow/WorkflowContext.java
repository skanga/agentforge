package com.skanga.workflow;

import com.skanga.workflow.exception.WorkflowInterrupt;
import com.skanga.workflow.persistence.WorkflowPersistence;

import java.util.Map;
import java.util.Objects;

/**
 * Provides context to a {@link com.skanga.workflow.graph.Node} during its execution
 * within a {@link Workflow}.
 *
 * <p>The context includes:
 * <ul>
 *   <li>The ID of the overall workflow instance.</li>
 *   <li>The ID of the current node being executed.</li>
 *   <li>The current {@link WorkflowState} (representing the state *before* the current node runs).</li>
 *   <li>Access to a {@link WorkflowPersistence} layer for saving interruptible state.</li>
 *   <li>Information about whether the workflow is currently resuming from an interruption ({@link #isResuming()}).</li>
 *   <li>Any feedback provided when the workflow was resumed, targeted at the current node ({@link #getFeedbackForNode()}).</li>
 * </ul>
 * </p>
 *
 * <p>Nodes use the context to access shared workflow data, interact with persistence if needed,
 * and signal interruptions using the {@link #interrupt(Map)} method.</p>
 *
 * <p>The workflow engine is responsible for creating and managing {@code WorkflowContext} instances,
 * updating fields like {@code currentNodeId} and resume state as the workflow progresses.</p>
 */
public class WorkflowContext {

    private final String workflowId;
    private String currentNodeId; // The ID of the node this context is currently for.
    private final WorkflowPersistence persistence;
    private final WorkflowState currentState; // State *entering* the current node. Immutable view or copy recommended for node.
    private boolean isResuming; // Is this node execution part of a resume flow?
    private Object feedbackForNode; // Feedback provided at resume, targeted for this node.

    /**
     * Constructs a WorkflowContext.
     *
     * @param workflowId        The unique ID of the current workflow instance. Must not be null.
     * @param initialNodeId     The ID of the node for which this context is initially created. Must not be null.
     * @param initialState      The {@link WorkflowState} as it is when entering this node. Must not be null.
     *                          The context holds a reference to this state. If nodes are expected to modify state
     *                          and these modifications should be isolated, `initialState` should be a copy.
     *                          Typically, a node returns a *new* state object after its run.
     * @param persistence       The {@link WorkflowPersistence} layer for saving/loading state on interrupts. Can be null
     *                          if the workflow or this part of it does not support persistence.
     * @param isResuming        True if this node execution is the first step in a resume operation.
     * @param feedbackForNode   Feedback data provided when resuming, specifically for this node. Can be null.
     */
    public WorkflowContext(String workflowId, String initialNodeId, WorkflowState initialState,
                           WorkflowPersistence persistence, boolean isResuming, Object feedbackForNode) {
        this.workflowId = Objects.requireNonNull(workflowId, "Workflow ID cannot be null for WorkflowContext.");
        this.currentNodeId = Objects.requireNonNull(initialNodeId, "Initial Node ID cannot be null for WorkflowContext.");
        this.currentState = Objects.requireNonNull(initialState, "Initial WorkflowState cannot be null for WorkflowContext.");
        this.persistence = persistence; // Persistence can be null
        this.isResuming = isResuming;
        this.feedbackForNode = feedbackForNode;
    }

    /**
     * Convenience constructor for starting a new workflow run (not resuming), with persistence.
     *
     * @param workflowId      The workflow instance ID.
     * @param initialNodeId   The ID of the first node to run.
     * @param initialState    The initial state for the workflow.
     * @param persistence     The persistence layer.
     */
    public WorkflowContext(String workflowId, String initialNodeId, WorkflowState initialState, WorkflowPersistence persistence) {
        this(workflowId, initialNodeId, initialState, persistence, false, null);
    }

    /**
     * Convenience constructor for starting a new workflow run (not resuming), without persistence.
     *
     * @param workflowId      The workflow instance ID.
     * @param initialNodeId   The ID of the first node to run.
     * @param initialState    The initial state for the workflow.
     */
    public WorkflowContext(String workflowId, String initialNodeId, WorkflowState initialState) {
        this(workflowId, initialNodeId, initialState, null, false, null);
    }


    /**
     * Signals that the current node's execution should be interrupted, typically to wait for external input.
     *
     * <p><b>Behavior during resume:</b> If {@link #isResuming()} is true and {@link #getFeedbackForNode()}
     * is not null (meaning feedback was provided for the current node upon resuming), this method
     * will *return* that feedback object and clear the resume state for this node. This allows
     * the node to "consume" the feedback once and continue its logic if appropriate.</p>
     *
     * <p><b>Behavior during normal execution (or resume without specific feedback):</b>
     * This method will throw a {@link WorkflowInterrupt}. The {@code WorkflowInterrupt} will contain:
     * <ul>
     *   <li>The {@code currentNodeId}.</li>
     *   <li>A copy of the {@code currentState} of the workflow *before* this node executed,
     *       potentially merged with the {@code dataToSave} provided here.</li>
     *   <li>The {@code dataToSave} map itself.</li>
     * </ul>
     * The workflow engine is expected to catch this interrupt, use the {@link WorkflowPersistence}
     * layer to save the {@code WorkflowInterrupt}'s details (especially its {@code workflowState}),
     * and halt execution.
     * </p>
     *
     * @param dataToSave Optional data (e.g., partial results, prompts for human) that the node
     *                   wants to ensure is saved as part of the workflow's state at the point of interruption.
     *                   This data is merged into a *copy* of the {@code currentState} before being packaged
     *                   into the {@code WorkflowInterrupt}. Can be null.
     * @return The feedback object if the node is currently consuming resume feedback.
     * @throws WorkflowInterrupt if not consuming resume feedback, to signal the interruption to the engine.
     */
    public Object interrupt(Map<String, Object> dataToSave) throws WorkflowInterrupt {
        if (isResuming && feedbackForNode != null) {
            Object feedback = this.feedbackForNode;
            this.feedbackForNode = null; // Consume the feedback
            this.isResuming = false;     // This specific node's "resume with feedback" state is now consumed
            return feedback;
        }

        // Create a state snapshot for saving. Start with a copy of the state as it was when the node began.
        WorkflowState stateToSaveInInterrupt = currentState.copy();
        if (dataToSave != null) {
            stateToSaveInInterrupt.putAll(dataToSave); // Merge any data the node wants to save now
        }
        // The WorkflowInterrupt captures the node ID, the (potentially augmented) state, and the dataToSave itself.
        throw new WorkflowInterrupt(dataToSave, this.currentNodeId, stateToSaveInInterrupt);
    }

    // --- Getters ---

    /** @return The unique ID of the current workflow instance. */
    public String getWorkflowId() { return workflowId; }

    /** @return The ID of the node this context is currently associated with. */
    public String getCurrentNodeId() { return currentNodeId; }

    /**
     * @return The {@link WorkflowPersistence} layer.
     * @throws IllegalStateException if no persistence layer was configured. Use {@link #hasPersistence()} to check.
     */
    public WorkflowPersistence getPersistence() {
        if (persistence == null) {
            throw new IllegalStateException("No persistence layer configured for this workflow context. Cannot save/load interrupt state.");
        }
        return persistence;
    }

    /** @return True if a persistence layer is available, false otherwise. */
    public boolean hasPersistence() {
        return persistence != null;
    }

    /**
     * @return The {@link WorkflowState} as it was when entering the current node's execution.
     *         Nodes typically operate on this state (or a copy) and return a new state.
     */
    public WorkflowState getCurrentState() {
        return currentState;
    }

    /**
     * @return True if the current node execution is part of resuming a previously interrupted workflow,
     *         false otherwise.
     */
    public boolean isResuming() {
        return isResuming;
    }

    /**
     * @return The feedback object provided when the workflow was resumed, specifically targeted
     *         at the current node. Returns null if not resuming or if no specific feedback was given.
     *         A node can consume this feedback by calling {@link #interrupt(Map)}.
     */
    public Object getFeedbackForNode() {
        return feedbackForNode;
    }

    // --- Package-private Setters/Updaters (intended for Workflow Engine use) ---

    /**
     * Updates the current node ID within this context.
     * Called by the workflow engine as it transitions between nodes.
     * @param currentNodeId The new current node ID.
     */
    void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    /**
     * Sets the resume state for the *next* node to be executed.
     * Called by the workflow engine when preparing to resume.
     * @param isResuming True if the next node execution is part of a resume.
     * @param feedbackForNextNode Feedback for that next node.
     */
    void setResumingState(boolean isResuming, Object feedbackForNextNode) {
        this.isResuming = isResuming;
        this.feedbackForNode = feedbackForNextNode;
    }
}

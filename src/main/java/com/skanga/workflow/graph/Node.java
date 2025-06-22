package com.skanga.workflow.graph;

import com.skanga.workflow.WorkflowContext;
import com.skanga.workflow.WorkflowState;
import com.skanga.workflow.exception.WorkflowInterrupt;
import com.skanga.workflow.exception.WorkflowException; // For general node execution errors

/**
 * Represents a node within a workflow graph. Each node defines a distinct unit of work
 * or a step in the overall process.
 *
 * <p>When a workflow executes, it traverses from node to node based on {@link Edge}
 * definitions and their conditions. The {@link #run(WorkflowContext)} method of the
 * current node is invoked to perform its action.</p>
 *
 * <p>Nodes can:
 * <ul>
 *   <li>Read from the current {@link WorkflowState} via the {@link WorkflowContext}.</li>
 *   <li>Perform computations, I/O operations, or interact with other services (like AI models).</li>
 *   <li>Update the workflow state by returning a new {@code WorkflowState} instance (or a modified copy).</li>
 *   <li>Signal a controlled pause in the workflow (e.g., to wait for human input) by throwing
 *       a {@link WorkflowInterrupt} via {@link WorkflowContext#interrupt(java.util.Map)}.</li>
 *   <li>Handle being resumed after an interruption, potentially using feedback provided at resume time.</li>
 * </ul>
 * </p>
 *
 * <p>Implementations should typically extend {@link AbstractNode} which provides ID management.</p>
 */
public interface Node {

    /**
     * Gets the unique identifier for this node within the workflow.
     * This ID is used to define edges and to identify the node in persistence or logging.
     *
     * @return The unique node ID string.
     */
    String getId();

    /**
     * Executes the primary logic of this node.
     *
     * <p>The node receives the current {@link WorkflowContext}, which provides access to the
     * {@link WorkflowState} (data flowing through the workflow), workflow metadata, and
     * mechanisms for interruption and persistence.</p>
     *
     * <p>A node is expected to return the resulting {@code WorkflowState} after its execution.
     * This could be a new {@code WorkflowState} instance or a modified version of the input state.
     * If the node does not modify the state, it can return the {@code context.getCurrentState()}.</p>
     *
     * <p><b>Interruption Handling:</b>
     * If a node needs to pause the workflow (e.g., to wait for external human input),
     * it should call {@link WorkflowContext#interrupt(java.util.Map)}. This method will throw
     * a {@link WorkflowInterrupt}. The workflow engine is responsible for catching this,
     * persisting the workflow's state (including any data provided in the interrupt call),
     * and halting further execution until the workflow is resumed.
     * </p>
     *
     * <p><b>Resuming:</b>
     * If a workflow is resumed at this node, {@code context.isResuming()} will be true,
     * and {@code context.getFeedbackForNode()} may contain data provided when the workflow
     * was resumed. The node can use {@code context.interrupt(null)} to "consume" this
     * feedback and continue, or it can use the feedback in its logic.
     * </p>
     *
     * @param context The current context of the workflow execution. Provides access to the
     *                current state, workflow ID, persistence layer, and resume information.
     * @return The {@link WorkflowState} that results from this node's execution.
     *         This will be the input state for the next node in the workflow.
     * @throws WorkflowInterrupt if the node's execution is intentionally paused (e.g., to wait for human input).
     *                           The workflow engine must handle this by saving state.
     * @throws WorkflowException if any other unrecoverable runtime error occurs during the node's execution.
     *                           This will typically halt the workflow and mark it as failed.
     */
    WorkflowState run(WorkflowContext context) throws WorkflowInterrupt, WorkflowException;
}

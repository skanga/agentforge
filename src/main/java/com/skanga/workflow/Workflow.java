package com.skanga.workflow;

import com.skanga.core.AgentObserver;
import com.skanga.core.ObservableAgentComponent;
import com.skanga.core.ObservableSupport;
import com.skanga.workflow.exception.WorkflowException;
import com.skanga.workflow.exception.WorkflowInterrupt;
import com.skanga.workflow.graph.Edge;
import com.skanga.workflow.graph.Node;
import com.skanga.workflow.persistence.InMemoryWorkflowPersistence;
import com.skanga.workflow.persistence.WorkflowPersistence;
import com.skanga.workflow.persistence.WorkflowPersistenceException;


import java.util.ArrayList;
import java.util.Collections; // For unmodifiable collections
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate; // For Edge constructor overload
import java.util.stream.Collectors;

/**
 * Orchestrates the execution of a graph-based workflow.
 * A workflow consists of {@link Node}s connected by {@link Edge}s.
 *
 * <p><b>Execution Flow:</b>
 * <ul>
 *   <li>The workflow starts from a designated {@code startNodeId}.</li>
 *   <li>It traverses the graph by executing nodes and following edges whose conditions are met.</li>
 *   <li>The {@link WorkflowState} is passed between nodes, allowing them to share data.</li>
 *   <li>Execution can be paused if a node throws a {@link WorkflowInterrupt} (e.g., for human input).
 *       The workflow's state can be persisted using a {@link WorkflowPersistence} layer and later resumed.</li>
 *   <li>The workflow may have an optional {@code endNodeId}. If defined, reaching this node
 *       (and executing it) signifies normal completion. If no {@code endNodeId} is set, the workflow
 *       ends when no more valid outgoing edges can be found from the current node.</li>
 * </ul>
 * </p>
 *
 * <p><b>Observability:</b>
 * This class implements {@link ObservableAgentComponent}, allowing {@link AgentObserver}s
 * to be registered to listen for various workflow lifecycle events, such as:
 * <ul>
 *   <li>{@code workflow-run-start}, {@code workflow-run-stop}, {@code workflow-run-error}</li>
 *   <li>{@code workflow-resume-start}, {@code workflow-resume-stop}, {@code workflow-resume-error}</li>
 *   <li>{@code workflow-node-start}, {@code workflow-node-stop}, {@code workflow-node-interrupt}, {@code workflow-node-error}</li>
 *   <li>{@code workflow-edge-traversed}, {@code workflow-no-condition-met}</li>
 * </ul>
 * </p>
 */
public class Workflow implements ObservableAgentComponent {

    private final String workflowId;
    private final Map<String, Node> nodes = new HashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private String startNodeId;
    private String endNodeId; // Optional: marks a specific terminal node

    private final WorkflowPersistence persistence;
    private final ObservableSupport observableSupport;

    /**
     * Constructs a Workflow with a specific ID and persistence layer.
     *
     * @param workflowId  A unique identifier for this workflow instance. If null or empty, a random UUID is generated.
     * @param persistence The {@link WorkflowPersistence} layer to use for saving and loading interrupt states.
     *                    If null, an {@link InMemoryWorkflowPersistence} instance will be used by default.
     */
    public Workflow(String workflowId, WorkflowPersistence persistence) {
        this.workflowId = (workflowId == null || workflowId.trim().isEmpty()) ? UUID.randomUUID().toString() : workflowId.trim();
        this.persistence = (persistence == null) ? new InMemoryWorkflowPersistence() : persistence;
        this.observableSupport = new ObservableSupport(this); // `this` is the source of observable events
    }

    /**
     * Constructs a Workflow with a specific persistence layer and a generated ID.
     * @param persistence The persistence layer. Defaults to {@link InMemoryWorkflowPersistence} if null.
     */
    public Workflow(WorkflowPersistence persistence) {
        this(null, persistence);
    }

    /**
     * Constructs a Workflow with a generated ID and default {@link InMemoryWorkflowPersistence}.
     */
    public Workflow() {
        this(null, null);
    }

    // --- Graph Definition Methods ---

    /**
     * Adds a node to the workflow graph.
     * @param node The {@link Node} to add. Must not be null, and its ID must be unique within the workflow.
     * @return This {@code Workflow} instance for fluent building.
     * @throws WorkflowException if a node with the same ID already exists.
     * @throws NullPointerException if node or node ID is null.
     */
    public Workflow addNode(Node node) {
        Objects.requireNonNull(node, "Node to add cannot be null.");
        Objects.requireNonNull(node.getId(), "Node ID cannot be null when adding to workflow.");
        if (nodes.containsKey(node.getId())) {
            throw new WorkflowException("Node with ID '" + node.getId() + "' already exists in the workflow '" + this.workflowId + "'.");
        }
        this.nodes.put(node.getId(), node);
        return this;
    }

    /**
     * Adds an unconditional edge between two nodes.
     * @param fromNodeId The ID of the source node.
     * @param toNodeId   The ID of the target node.
     * @return This {@code Workflow} instance.
     * @throws WorkflowException if source or target node IDs are not found in the workflow.
     */
    public Workflow addEdge(String fromNodeId, String toNodeId) {
        return addEdge(new Edge(fromNodeId, toNodeId));
    }

    /**
     * Adds a conditional edge between two nodes.
     * @param fromNodeId The ID of the source node.
     * @param toNodeId   The ID of the target node.
     * @param condition  A {@link Predicate<WorkflowState>} determining if the edge should be traversed.
     * @return This {@code Workflow} instance.
     * @throws WorkflowException if source or target node IDs are not found.
     */
    public Workflow addEdge(String fromNodeId, String toNodeId, Predicate<WorkflowState> condition) {
        return addEdge(new Edge(fromNodeId, toNodeId, condition));
    }

    /**
     * Adds a pre-constructed {@link Edge} to the workflow.
     * @param edge The edge to add.
     * @return This {@code Workflow} instance.
     * @throws WorkflowException if nodes specified in the edge are not found.
     * @throws NullPointerException if edge is null.
     */
    public Workflow addEdge(Edge edge) {
        Objects.requireNonNull(edge, "Edge to add cannot be null.");
        if (!nodes.containsKey(edge.getFromNodeId())) {
            throw new WorkflowException("Source node '" + edge.getFromNodeId() + "' for edge not found in workflow '" + this.workflowId + "'.");
        }
        if (!nodes.containsKey(edge.getToNodeId())) {
            throw new WorkflowException("Target node '" + edge.getToNodeId() + "' for edge not found in workflow '" + this.workflowId + "'.");
        }
        this.edges.add(edge);
        return this;
    }

    /**
     * Sets the ID of the starting node for this workflow.
     * @param startNodeId The ID of an existing node to be the start point.
     * @return This {@code Workflow} instance.
     * @throws WorkflowException if the specified startNodeId does not correspond to an added node.
     */
    public Workflow setStartNodeId(String startNodeId) {
        Objects.requireNonNull(startNodeId, "Start node ID cannot be null.");
        if (!nodes.containsKey(startNodeId)) {
            throw new WorkflowException("Attempted to set start node ID to '" + startNodeId + "', but no such node exists in workflow '" + this.workflowId + "'.");
        }
        this.startNodeId = startNodeId;
        return this;
    }

    /**
     * Sets the ID of the designated end node for this workflow.
     * Reaching and executing this node signifies a normal completion path.
     * An end node is optional; a workflow can also end if a node has no valid outgoing edges.
     * @param endNodeId The ID of an existing node. It's not strictly validated for existence here
     *                  to allow defining it before all nodes are added, but {@link #validateGraphStructure()} will check.
     * @return This {@code Workflow} instance.
     */
    public Workflow setEndNodeId(String endNodeId) {
        this.endNodeId = endNodeId; // Validity checked in validateGraphStructure
        return this;
    }

    /** @return The unique ID of this workflow instance. */
    public String getId() { return workflowId; }
    /** @return An unmodifiable view of the nodes in this workflow, mapped by their IDs. */
    public Map<String, Node> getNodes() { return Collections.unmodifiableMap(nodes); }
    /** @return An unmodifiable list of the edges in this workflow. */
    public List<Edge> getEdges() { return Collections.unmodifiableList(edges); }
    /** @return The ID of the designated start node. */
    public String getStartNodeId() { return startNodeId; }
    /** @return The ID of the designated end node, or null if not set. */
    public String getEndNodeId() { return endNodeId; }
    /** @return The persistence layer configured for this workflow. */
    public WorkflowPersistence getPersistence() { return persistence; }


    /**
     * Validates the basic structural integrity of the workflow graph.
     * Checks if a start node is defined and exists.
     * Warns if a defined end node does not exist.
     * More complex validations (reachability, cycles, orphaned nodes) could be added.
     * @throws WorkflowException if critical structural issues are found (e.g., no start node).
     */
    public void validateGraphStructure() throws WorkflowException {
        if (startNodeId == null || startNodeId.trim().isEmpty()) {
            throw new WorkflowException("Start node ID has not been set for workflow '" + this.workflowId + "'.");
        }
        if (!nodes.containsKey(startNodeId)) {
            throw new WorkflowException("The configured start node ID '" + startNodeId + "' does not match any added node in workflow '" + this.workflowId + "'.");
        }
        if (endNodeId != null && !endNodeId.trim().isEmpty() && !nodes.containsKey(endNodeId)) {
            // This is a warning rather than an exception, as an end node is optional,
            // and the graph might still be valid if it terminates through other means.
            System.err.println("Warning for workflow '" + this.workflowId + "': Configured end node ID '" + endNodeId + "' does not match any added node.");
        }
        // Add cycle detection during validation
        detectCyclesInGraph();
    }

    private void detectCyclesInGraph() throws WorkflowException {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String nodeId : nodes.keySet()) {
            if (!visited.contains(nodeId)) {
                if (hasCycleDFS(nodeId, visited, recursionStack)) {
                    throw new WorkflowException("Cycle detected in workflow graph starting from node: " + nodeId);
                }
            }
        }
    }

    private boolean hasCycleDFS(String nodeId, Set<String> visited, Set<String> recursionStack) {
        visited.add(nodeId);
        recursionStack.add(nodeId);

        for (Edge edge : edges) {
            if (edge.getFromNodeId().equals(nodeId)) {
                String neighbor = edge.getToNodeId();
                if (!visited.contains(neighbor)) {
                    if (hasCycleDFS(neighbor, visited, recursionStack)) {
                        return true;
                    }
                } else if (recursionStack.contains(neighbor)) {
                    return true;
                }
            }
        }

        recursionStack.remove(nodeId);
        return false;
    }

    // --- Execution Methods ---

    /**
     * Runs the workflow starting from the {@code startNodeId} with the given initial state.
     *
     * @param initialState The {@link WorkflowState} to begin the workflow with. Must not be null.
     * @return The final {@link WorkflowState} after the workflow completes.
     * @throws WorkflowException if a structural issue is found, a node execution error occurs (not an interrupt),
     *                           or if the workflow cannot proceed (e.g., dead end, no conditions met).
     * @throws WorkflowInterrupt if a node explicitly interrupts the workflow (e.g., for human input).
     *                           The state will be saved by the persistence layer if configured.
     */
    public WorkflowState run(WorkflowState initialState) throws WorkflowException, WorkflowInterrupt {
        Objects.requireNonNull(initialState, "Initial WorkflowState cannot be null for run().");
        validateGraphStructure();

        Map<String, Object> eventContext = Map.of(
            "workflowId", workflowId,
            "startNodeId", startNodeId,
            "initialStateKeys", initialState.getAll().keySet().toString() // Example of state data
        );
        notifyObservers("workflow-run-start", eventContext);

        try {
            // Start execution loop with no initial context (it's not a resume)
            WorkflowState finalState = executeLoop(this.startNodeId, initialState, null);
            notifyObservers("workflow-run-stop", Map.of("workflowId", workflowId, "status", "completed", "finalStateKeys", finalState.getAll().keySet().toString()));
            return finalState;
        } catch (WorkflowInterrupt e) {
            // executeLoop already handles saving on interrupt. Re-throw to signal interruption to caller.
            notifyObservers("workflow-run-interrupted", Map.of("workflowId", workflowId, "nodeId", e.getCurrentNodeId()));
            throw e;
        } catch (RuntimeException e) { // Catch our own or other runtime issues
            notifyObservers("workflow-run-error", Map.of("workflowId", workflowId, "error", e.toString()));
            throw e; // Re-throw after notification
        }
    }

    /**
     * Resumes a previously interrupted workflow.
     * It loads the saved state using the configured {@link WorkflowPersistence} layer
     * and continues execution from the node where the interruption occurred.
     *
     * @param humanFeedback Feedback or input provided by a human (or external system) that allows
     *                      the interrupted node to continue. This is passed to the node via {@link WorkflowContext}.
     * @return The final {@link WorkflowState} after the workflow completes.
     * @throws WorkflowException if loading state fails, no persisted state is found, a node execution error occurs,
     *                           or if the workflow cannot proceed.
     * @throws WorkflowInterrupt if a node explicitly interrupts the workflow again during resumption.
     */
    public WorkflowState resume(Object humanFeedback) throws WorkflowException, WorkflowInterrupt {
        notifyObservers("workflow-resume-start", Map.of("workflowId", workflowId, "hasFeedback", humanFeedback != null));
        WorkflowInterrupt interruptData;
        try {
            interruptData = persistence.load(this.workflowId);
        } catch (WorkflowPersistenceException e) {
            notifyObservers("workflow-resume-error", Map.of("workflowId", workflowId, "reason", "Failed to load persisted state", "error", e.getMessage()));
            throw new WorkflowException("Failed to load persisted state for workflow ID '" + this.workflowId + "'. Cannot resume.", e);
        }

        if (interruptData == null) {
            notifyObservers("workflow-resume-error", Map.of("workflowId", workflowId, "reason", "No persisted state found."));
            throw new WorkflowException("No persisted state found to resume workflow ID '" + this.workflowId + "'.");
        }

        String resumeNodeId = interruptData.getCurrentNodeId();
        WorkflowState resumedState = interruptData.getWorkflowState().copy(); // Use a copy of the loaded state

        // Prepare context for the first node being resumed, including the feedback.
        WorkflowContext initialResumedContext = new WorkflowContext(workflowId, resumeNodeId, resumedState, persistence, true, humanFeedback);

        try {
            WorkflowState finalState = executeLoop(resumeNodeId, resumedState, initialResumedContext);
            // If executeLoop completes without further interruption, the workflow is done.
            // Delete the persisted state as it's no longer needed.
            try {
                persistence.delete(this.workflowId);
            } catch (WorkflowPersistenceException e) {
                // Log warning but don't fail the overall successful resumption.
                System.err.println("Warning: Workflow '" + this.workflowId + "' completed after resume, but failed to delete persisted interrupt state: " + e.getMessage());
            }
            notifyObservers("workflow-resume-stop", Map.of("workflowId", workflowId, "status", "completed", "finalStateKeys", finalState.getAll().keySet().toString()));
            return finalState;
        } catch (WorkflowInterrupt e) {
            // Interruption during resumed execution; state already saved by executeLoop. Re-throw.
            notifyObservers("workflow-resume-interrupted", Map.of("workflowId", workflowId, "nodeId", e.getCurrentNodeId()));
            throw e;
        } catch (RuntimeException e) {
            notifyObservers("workflow-resume-error", Map.of("workflowId", workflowId, "reason", "Error during resumed execution", "error", e.toString()));
            throw e; // Re-throw after notification
        }
    }

    /**
     * The main execution loop for the workflow.
     *
     * @param startingNodeId     The ID of the node to start (or resume) execution from.
     * @param initialLoopState   The workflow state to begin this loop with.
     * @param initialContext     If resuming, this is the context for the very first node execution,
     *                           containing resume flags and feedback. Null if starting a new run.
     * @return The final workflow state after the loop completes (reaches endNode or a terminal node).
     * @throws WorkflowInterrupt If a node interrupts execution. The state will be saved by this method.
     * @throws WorkflowException If any other runtime error or graph traversal issue occurs.
     */
    private WorkflowState executeLoop(String startingNodeId, WorkflowState initialLoopState, WorkflowContext initialContext) throws WorkflowInterrupt {
        String currentNodeId = startingNodeId;
        WorkflowState currentLoopState = initialLoopState.copy(); // Operate on a mutable copy for this execution run
        boolean isFirstNodeInPotentiallyResumedFlow = (initialContext != null && initialContext.isResuming());

        // Basic cycle detection for current path
        int executionSteps = 0;
        final int MAX_EXECUTION_STEPS = nodes.size() * 10; // Reasonable upper bound

        while (currentNodeId != null && (this.endNodeId == null || !currentNodeId.equals(this.endNodeId))) {
            executionSteps++;
            if (executionSteps > MAX_EXECUTION_STEPS) {
                notifyObservers("workflow-run-error", Map.of("workflowId", workflowId, "nodeId", currentNodeId, "error", "Maximum execution steps exceeded - possible infinite loop"));
                throw new WorkflowException("Workflow '" + workflowId + "' exceeded maximum execution steps (" + MAX_EXECUTION_STEPS + "). Possible infinite loop detected.");
            }

            Node currentNode = nodes.get(currentNodeId);
            if (currentNode == null) {
                throw new WorkflowException("Node ID '" + currentNodeId + "' not found in workflow '" + this.workflowId + "' graph during execution.");
            }

            WorkflowContext nodeContext;
            if (isFirstNodeInPotentiallyResumedFlow) {
                nodeContext = initialContext; // Use the specially prepared context for the first resumed node
                nodeContext.setCurrentNodeId(currentNodeId); // Ensure it points to current node
                isFirstNodeInPotentiallyResumedFlow = false; // Flag consumed
            } else {
                // For subsequent nodes, or if not resuming, create a fresh context for the step.
                // Pass false for isResuming, null for feedback.
                nodeContext = new WorkflowContext(this.workflowId, currentNodeId, currentLoopState.copy(), this.persistence, false, null);
            }

            notifyObservers("workflow-node-start", Map.of("workflowId", workflowId, "nodeId", currentNodeId, "nodeClass", currentNode.getClass().getSimpleName()));
            try {
                currentLoopState = currentNode.run(nodeContext); // Node returns the new state
                Objects.requireNonNull(currentLoopState, "Node " + currentNodeId + " run() method returned null WorkflowState.");
                notifyObservers("workflow-node-stop", Map.of("workflowId", workflowId, "nodeId", currentNodeId, "status", "completed"));
            } catch (WorkflowInterrupt interrupt) {
                // Node signaled an interruption.
                notifyObservers("workflow-node-interrupt", Map.of("workflowId", workflowId, "nodeId", interrupt.getCurrentNodeId(), "interruptDataKeys", interrupt.getDataToSave().keySet()));
                // Persist the state provided by the interrupt itself, as it might have merged dataToSave.
                try {
                    if (this.persistence != null) { // Check if persistence is configured
                         this.persistence.save(this.workflowId, interrupt);
                    } else {
                        // Log if interrupt occurs but no persistence is available. State will be lost.
                        System.err.println("Warning: Workflow '" + workflowId + "' interrupted at node '" + interrupt.getCurrentNodeId() +
                                           "' but no persistence layer is configured. State will not be saved.");
                    }
                } catch (WorkflowPersistenceException e) {
                    // Wrap persistence error in a WorkflowException that indicates the node context
                    throw new WorkflowException("Failed to save workflow state during interrupt at node '" + interrupt.getCurrentNodeId() +
                                                "' in workflow '" + this.workflowId + "': " + e.getMessage(), e);
                }
                throw interrupt; // Re-throw to signal the calling method (run/resume) to halt.
            } catch (Exception e) { // Catch other runtime exceptions from node.run()
                 notifyObservers("workflow-node-error", Map.of("workflowId", workflowId, "nodeId", currentNodeId, "error", e.toString()));
                throw new WorkflowException("Error executing node '" + currentNodeId + "' in workflow '" + this.workflowId + "': " + e.getMessage(), e);
            }

            // Find the next node based on edges and current state
            String previousNodeId = currentNodeId; // For logging/event
            currentNodeId = findNextNode(previousNodeId, currentLoopState);

            if (currentNodeId != null) {
                notifyObservers("workflow-edge-traversed", Map.of("workflowId", workflowId, "fromNode", previousNodeId, "toNode", currentNodeId));
            }
            // If currentNodeId is null, the loop will terminate.
        } // End of while loop

        // If loop terminated because currentNodeId is now the endNodeId
        if (this.endNodeId != null && this.endNodeId.equals(currentNodeId)) {
            Node finalNode = nodes.get(this.endNodeId);
            // End node should always exist if endNodeId is set and graph validated, but check defensively.
            if (finalNode != null) {
                 WorkflowContext finalNodeContext = new WorkflowContext(this.workflowId, this.endNodeId, currentLoopState.copy(), this.persistence, false, null);
                 notifyObservers("workflow-node-start", Map.of("workflowId", workflowId, "nodeId", this.endNodeId, "nodeClass", finalNode.getClass().getSimpleName()));
                 try {
                    currentLoopState = finalNode.run(finalNodeContext); // Execute the designated end node
                    Objects.requireNonNull(currentLoopState, "End node " + this.endNodeId + " run() method returned null WorkflowState.");
                    notifyObservers("workflow-node-stop", Map.of("workflowId", workflowId, "nodeId", this.endNodeId, "status", "completed"));
                 } catch (WorkflowInterrupt interrupt) {
                     notifyObservers("workflow-node-interrupt", Map.of("workflowId", workflowId, "nodeId", this.endNodeId, "interruptDataKeys", interrupt.getDataToSave().keySet()));
                     try {
                        if (this.persistence != null) this.persistence.save(this.workflowId, interrupt);
                     } catch (WorkflowPersistenceException e) {
                         throw new WorkflowException("Failed to save state during interrupt at end node '" + this.endNodeId + "': " + e.getMessage(), e);
                     }
                     throw interrupt; // End node interrupted.
                 } catch (Exception e) {
                     notifyObservers("workflow-node-error", Map.of("workflowId", workflowId, "nodeId", this.endNodeId, "error", e.toString()));
                     throw new WorkflowException("Error executing end node '" + this.endNodeId + "': " + e.getMessage(), e);
                 }
            } else {
                 throw new WorkflowException("Designated end node ID '" + this.endNodeId + "' not found in graph for final execution step.");
            }
        } else if (currentNodeId == null && this.endNodeId != null) {
            // Loop terminated because findNextNode returned null, but we haven't reached the designated endNode.
            // This implies a dead-end in the graph before the intended finish.
            throw new WorkflowException("Workflow '" + this.workflowId + "' execution finished without reaching the designated end node '" + this.endNodeId + "'.");
        }
        // If loop terminated (currentNodeId is null) and no endNodeId was set, this is a valid completion.

        // If workflow completes fully (not interrupted), delete any persisted interrupt state.
        try {
            if (this.persistence != null) { // Check if persistence layer exists
                this.persistence.delete(this.workflowId);
            }
        } catch (WorkflowPersistenceException e) {
            // Log this as a warning, but don't let it fail the overall workflow result at this stage.
            System.err.println("Warning: Workflow '" + this.workflowId + "' completed, but failed to delete persisted interrupt state: " + e.getMessage());
        }
        return currentLoopState; // Return the final state
    }

    /**
     * Finds the next node to execute based on outgoing edges from the current node
     * and their conditions evaluated against the current workflow state.
     *
     * @param currentExecutingNodeId The ID of the node that has just finished execution.
     * @param state            The current {@link WorkflowState} after the {@code currentExecutingNodeId}'s execution.
     * @return The ID of the next node to execute, or {@code null} if no valid path is found
     *         (e.g., dead end, or it's the designated end node with no further explicit path).
     * @throws WorkflowException if no outgoing edges are defined from a non-end node, or if multiple
     *                           unconditional edges exist (or multiple conditional edges evaluate to true
     *                           and this behavior is not configured - current implementation takes first true).
     */
    private String findNextNode(String currentExecutingNodeId, WorkflowState state) {
        List<Edge> outgoingEdges = this.edges.stream()
                .filter(edge -> edge.getFromNodeId().equals(currentExecutingNodeId))
                .collect(Collectors.toList());

        if (outgoingEdges.isEmpty()) {
            // No outgoing edges. If this is the designated endNodeId, it's a valid termination.
            if (currentExecutingNodeId.equals(this.endNodeId)) {
                return null; // Signal completion at designated end node
            }
            // If no endNodeId is set, any node without outgoing edges is a terminal node.
            if (this.endNodeId == null) {
                notifyObservers("workflow-terminal-node-reached", Map.of("workflowId", workflowId, "nodeId", currentExecutingNodeId));
                return null; // Signal completion
            }
            // Otherwise, it's a dead end before reaching the designated endNodeId.
            throw new WorkflowException("No outgoing edges from node '" + currentExecutingNodeId + "' in workflow '" + this.workflowId + "', and it is not the designated end node.");
        }

        List<Edge> executableEdges = new ArrayList<>();
        for (Edge edge : outgoingEdges) {
            if (edge.shouldExecute(state)) {
                executableEdges.add(edge);
            }
        }

        if (executableEdges.isEmpty()) {
            // No conditions met on any outgoing edges from this node.
            notifyObservers("workflow-no-condition-met", Map.of("workflowId", workflowId, "nodeId", currentExecutingNodeId, "stateKeys", state.getAll().keySet()));
            throw new WorkflowException("No conditions met for any outgoing edge from node '" + currentExecutingNodeId + "' in workflow '" + this.workflowId + "'. Workflow cannot proceed.");
        }
        if (executableEdges.size() > 1) {
            // Multiple paths are valid. The current simple engine takes the first one defined.
            // A more advanced engine might support parallel splits or priority-based selection.
            String chosenTarget = executableEdges.get(0).getToNodeId();
            List<String> possibleTargets = executableEdges.stream().map(Edge::getToNodeId).collect(Collectors.toList());
            notifyObservers("workflow-multiple-conditions-met",
                Map.of("workflowId", workflowId, "nodeId", currentExecutingNodeId,
                       "chosenTarget", chosenTarget, "possibleTargets", possibleTargets));
            System.err.println("Warning for workflow '" + this.workflowId + "': Multiple edges can be executed from node '" + currentExecutingNodeId +
                               "'. Taking the first one to node '" + chosenTarget + "'. Possible targets: " + possibleTargets);
            return chosenTarget;
        }
        return executableEdges.get(0).getToNodeId();
    }

    // --- ObservableAgentComponent Implementation ---
    @Override
    public void addObserver(AgentObserver observer, String eventFilter) {
        this.observableSupport.addObserver(observer, eventFilter);
    }

    @Override
    public void removeObserver(AgentObserver observer) {
        this.observableSupport.removeObserver(observer);
    }

    @Override
    public void notifyObservers(String eventType, Object eventData) {
        // Add workflowId to all events for easier correlation if not already present in eventData map
        Map<String, Object> enrichedEventData = new HashMap<>();
        if (eventData instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mapData = (Map<String, Object>) eventData;
            enrichedEventData.putAll(mapData);
        } else if (eventData != null) {
            enrichedEventData.put("data", eventData); // Wrap non-map data
        }
        enrichedEventData.putIfAbsent("workflowId", this.workflowId); // Ensure workflowId is present

        this.observableSupport.notifyObservers(eventType, Collections.unmodifiableMap(enrichedEventData));
    }
}

package com.skanga.workflow.graph;

import com.skanga.workflow.WorkflowState;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Represents a directed edge connecting two {@link Node}s in a workflow graph.
 * An edge defines a potential transition from a source node ({@code fromNodeId})
 * to a target node ({@code toNodeId}).
 *
 * <p>Each edge can optionally have a {@link Predicate<WorkflowState>} condition.
 * If a condition is present, the edge will only be considered traversable by the
 * workflow engine if the predicate evaluates to {@code true} given the current
 * {@link WorkflowState}. If no condition is set, the edge is considered unconditional.</p>
 *
 * <p>In cases where multiple edges originate from a single node, the workflow engine
 * will typically evaluate their conditions and choose one to traverse. If multiple
 * conditional edges evaluate to true, the engine's behavior (e.g., picking the first
 * one defined, throwing an error, or supporting parallel paths) depends on its specific
 * implementation (see {@link com.skanga.workflow.Workflow#findNextNode(String, WorkflowState)}).
 * </p>
 */
public class Edge {

    private final String fromNodeId;
    private final String toNodeId;
    /**
     * An optional condition that determines if this edge can be traversed.
     * The predicate takes the current {@link WorkflowState} as input.
     * If null, the edge is unconditional.
     */
    private final Predicate<WorkflowState> condition;

    /**
     * Creates a new edge with an optional condition.
     *
     * @param fromNodeId The ID of the source node (the node from which this edge originates). Must not be null.
     * @param toNodeId   The ID of the target node (the node to which this edge leads). Must not be null.
     * @param condition  A {@link Predicate} that tests the current {@link WorkflowState}.
     *                   If the predicate evaluates to {@code true}, the edge is traversable.
     *                   If {@code null}, the edge is considered unconditional.
     * @throws NullPointerException if {@code fromNodeId} or {@code toNodeId} is null.
     */
    public Edge(String fromNodeId, String toNodeId, Predicate<WorkflowState> condition) {
        this.fromNodeId = Objects.requireNonNull(fromNodeId, "Source node ID (fromNodeId) cannot be null for an Edge.");
        this.toNodeId = Objects.requireNonNull(toNodeId, "Target node ID (toNodeId) cannot be null for an Edge.");
        this.condition = condition; // Condition can be null for unconditional edges
    }

    /**
     * Creates an unconditional edge (an edge without a specific condition).
     * This edge will always be considered traversable if its source node has completed.
     *
     * @param fromNodeId The ID of the source node.
     * @param toNodeId   The ID of the target node.
     */
    public Edge(String fromNodeId, String toNodeId) {
        this(fromNodeId, toNodeId, null);
    }

    /** @return The ID of the source node of this edge. */
    public String getFromNodeId() {
        return fromNodeId;
    }

    /** @return The ID of the target node of this edge. */
    public String getToNodeId() {
        return toNodeId;
    }

    /**
     * @return The condition predicate for this edge, or {@code null} if the edge is unconditional.
     */
    public Predicate<WorkflowState> getCondition() {
        return condition;
    }

    /**
     * Evaluates whether this edge should be executed (traversed) based on the current workflow state.
     * If the edge has no condition (i.e., {@link #getCondition()} is null), it's always considered executable.
     * Otherwise, the provided state is tested against the edge's condition predicate.
     *
     * @param state The current {@link WorkflowState} to evaluate the condition against. Must not be null.
     * @return {@code true} if the edge should be traversed, {@code false} otherwise.
     * @throws NullPointerException if {@code state} is null and the edge has a condition.
     */
    public boolean shouldExecute(WorkflowState state) {
        if (condition == null) {
            return true; // Unconditional edge is always executable
        }
        Objects.requireNonNull(state, "WorkflowState cannot be null for evaluating a conditional edge.");
        return condition.test(state);
    }

    @Override
    public String toString() {
        return "Edge{" +
                "from='" + fromNodeId + '\'' +
                ", to='" + toNodeId + '\'' +
                ", hasCondition=" + (condition != null) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Edge edge = (Edge) o;
        return fromNodeId.equals(edge.fromNodeId) &&
               toNodeId.equals(edge.toNodeId) &&
               Objects.equals(condition, edge.condition); // Note: Predicate equality can be tricky.
                                                          // Default equals for lambdas is reference equality.
                                                          // For robust equality, predicates might need to be classes.
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromNodeId, toNodeId, condition);
    }
}

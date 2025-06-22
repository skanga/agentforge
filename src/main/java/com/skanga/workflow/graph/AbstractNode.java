package com.skanga.workflow.graph;

import com.skanga.workflow.WorkflowContext; // For run method signature in concrete classes

import java.util.Objects;

/**
 * An abstract base class for implementing {@link Node} in a workflow.
 * This class provides a final field for the node's ID and implements
 * {@link #getId()}, {@link #toString()}, {@link #equals(Object)}, and {@link #hashCode()}
 * based on this ID.
 *
 * <p>Concrete node implementations should extend this class and primarily need to
 * implement the {@link #run(WorkflowContext)} method to define their specific logic.</p>
 *
 * <p>Example subclass:</p>
 * <pre>{@code
 * public class MyCustomNode extends AbstractNode {
 *     public MyCustomNode(String id) {
 *         super(id);
 *     }
 *
 *     @Override
 *     public WorkflowState run(WorkflowContext context) throws WorkflowInterrupt {
 *         WorkflowState currentState = context.getCurrentState();
 *         // ... perform actions ...
 *         String name = (String) currentState.get("name");
 *         currentState.put("greeting", "Hello, " + name);
 *         return currentState; // Or a new WorkflowState
 *     }
 * }
 * }</pre>
 */
public abstract class AbstractNode implements Node {

    /** The unique identifier for this node within the workflow. */
    protected final String id;

    /**
     * Constructs an {@code AbstractNode} with a given ID.
     * @param id The unique identifier for this node. Must not be null or empty.
     * @throws NullPointerException if id is null.
     * @throws IllegalArgumentException if id is empty or only whitespace.
     */
    protected AbstractNode(String id) {
        Objects.requireNonNull(id, "Node ID cannot be null.");
        if (id.trim().isEmpty()) {
            throw new IllegalArgumentException("Node ID cannot be empty or whitespace.");
        }
        this.id = id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String getId() { // Make final as ID should be immutable for a node instance
        return id;
    }

    // The run(WorkflowContext context) method is declared in the Node interface
    // and must be implemented by concrete subclasses of AbstractNode.
    // public abstract WorkflowState run(WorkflowContext context) throws WorkflowInterrupt;

    /**
     * Returns a string representation of the node, typically including its class name and ID.
     * @return A string representation of the node.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[id='" + id + "']";
    }

    /**
     * Compares this node to another object for equality.
     * Two nodes are considered equal if they are of the same class and have the same ID.
     * @param o The object to compare with.
     * @return True if the objects are equal, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        // Note: Using getClass() != o.getClass() means subclasses are not equal to base or other subclasses
        // even with same ID. If ID should be unique across all Node types, one might use instanceof Node
        // and then just compare IDs, but class equality is often intended for nodes.
        if (o == null || getClass() != o.getClass()) return false;
        AbstractNode that = (AbstractNode) o;
        return id.equals(that.id);
    }

    /**
     * Returns a hash code value for the node, based on its ID.
     * @return A hash code value for this node.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

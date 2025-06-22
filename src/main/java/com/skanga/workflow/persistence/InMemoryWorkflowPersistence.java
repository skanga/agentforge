package com.skanga.workflow.persistence;

import com.skanga.workflow.exception.WorkflowInterrupt;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory implementation of {@link WorkflowPersistence}.
 * This class stores workflow interrupt states in a {@link ConcurrentHashMap} within RAM.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Simple and fast, as it avoids disk I/O or network latency.</li>
 *   <li>State is volatile: If the application restarts, all persisted states are lost.</li>
 *   <li>Thread-safe for concurrent access to the store from multiple workflow instances
 *       (though individual workflow execution is typically single-threaded per instance).</li>
 * </ul>
 * </p>
 *
 * <p>This implementation is suitable for:
 * <ul>
 *   <li>Testing and development.</li>
 *   <li>Scenarios where workflow state persistence across application restarts is not required.</li>
 *   <li>Workflows that are short-lived or where interruption and resumption happen within
 *       the same application session.</li>
 * </ul>
 * </p>
 */
public class InMemoryWorkflowPersistence implements WorkflowPersistence {

    private final Map<String, WorkflowInterrupt> store = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     * <p>Saves the interrupt state into an in-memory map, keyed by workflow ID.</p>
     * @throws NullPointerException if workflowId or interrupt data is null.
     */
    @Override
    public void save(String workflowId, WorkflowInterrupt interrupt) throws WorkflowPersistenceException {
        Objects.requireNonNull(workflowId, "Workflow ID cannot be null for saving state.");
        Objects.requireNonNull(interrupt, "WorkflowInterrupt data cannot be null for saving state.");
        store.put(workflowId, interrupt);
    }

    /**
     * {@inheritDoc}
     * <p>Loads interrupt state from the in-memory map using the workflow ID.</p>
     * @return The saved {@link WorkflowInterrupt} object, or {@code null} if no state is found for the ID.
     * @throws NullPointerException if workflowId is null.
     */
    @Override
    public WorkflowInterrupt load(String workflowId) throws WorkflowPersistenceException {
        Objects.requireNonNull(workflowId, "Workflow ID cannot be null for loading state.");
        return store.get(workflowId);
    }

    /**
     * {@inheritDoc}
     * <p>Removes the interrupt state associated with the workflow ID from the in-memory map.</p>
     * @throws NullPointerException if workflowId is null.
     */
    @Override
    public void delete(String workflowId) throws WorkflowPersistenceException {
        Objects.requireNonNull(workflowId, "Workflow ID cannot be null for deleting state.");
        store.remove(workflowId);
    }

    /**
     * Clears all persisted states from this in-memory store.
     * Useful for resetting state during testing or application shutdown if needed.
     */
    public void clearAll() {
        store.clear();
    }

    /**
     * Gets the number of workflow states currently persisted in memory.
     * @return The count of persisted workflow states.
     */
    public int size() {
        return store.size();
    }
}

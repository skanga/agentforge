package com.skanga.workflow;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the state of a workflow during its execution.
 * It acts as a container for arbitrary data that can be passed between
 * {@link com.skanga.workflow.graph.Node}s in a workflow.
 *
 * <p>The state is essentially a key-value store, where keys are strings and
 * values can be any object. Nodes can read from this state to get their inputs
 * and write to this state to store their outputs or modify data for subsequent nodes.</p>
 *
 * <p>Instances of {@code WorkflowState} are mutable. The {@link #copy()} method
 * can be used to create a snapshot or an independent copy of the state if needed,
 * for example, before passing it to a node that might modify it, or when persisting
 * an interrupt state.</p>
 *
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * WorkflowState state = new WorkflowState();
 * state.put("userInput", "What is the weather?");
 * state.put("location", "London");
 *
 * String location = (String) state.get("location");
 * // A node might then use this location to perform an action.
 *
 * // Another node might add its result to the state:
 * state.put("weatherResult", "Sunny with a high of 20°C");
 * }</pre>
 */
public class WorkflowState {

    /** The internal map holding the key-value pairs of the workflow state. */
    private final Map<String, Object> stateData;

    /**
     * Constructs a new, empty WorkflowState.
     */
    public WorkflowState() {
        this.stateData = new HashMap<>();
    }

    /**
     * Constructs a new WorkflowState initialized with data from the provided map.
     * A defensive copy of the initial data map is made.
     *
     * @param initialStateData A map containing initial key-value pairs for the state.
     *                         If null, an empty state is created.
     */
    public WorkflowState(Map<String, Object> initialStateData) {
        this.stateData = (initialStateData != null) ? new HashMap<>(initialStateData) : new HashMap<>();
    }

    /**
     * Retrieves a value from the workflow state by its key.
     *
     * @param key The key of the data to retrieve.
     * @return The value associated with the key, or {@code null} if the key is not found or the value is explicitly null.
     */
    public Object get(String key) {
        return stateData.get(key);
    }

    /**
     * Retrieves a value from the workflow state by its key, returning a default value if the key is not found.
     *
     * @param key          The key of the data to retrieve.
     * @param defaultValue The value to return if the key is not found in the state.
     * @param <T>          The expected type of the value.
     * @return The value associated with the key, or {@code defaultValue} if the key is not present.
     *         The caller is responsible for ensuring type consistency.
     */
    @SuppressWarnings("unchecked") // Type casting is inherent in this generic getOrDefault
    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) stateData.getOrDefault(key, defaultValue);
    }


    /**
     * Stores a key-value pair into the workflow state.
     * If the key already exists, its current value will be overwritten.
     *
     * @param key   The key for the data. Must not be null.
     * @param value The value to store. Can be null.
     * @throws NullPointerException if the key is null.
     */
    public void put(String key, Object value) {
        Objects.requireNonNull(key, "Key for WorkflowState cannot be null.");
        stateData.put(key, value);
    }

    /**
     * Puts all entries from the given map into the current workflow state.
     * Keys from the input map will overwrite existing keys in this state.
     *
     * @param dataMap A map containing key-value pairs to add to the state.
     *                If null, this method does nothing.
     */
    public void putAll(Map<String, Object> dataMap) {
        if (dataMap != null) {
            this.stateData.putAll(dataMap);
        }
    }

    /**
     * Checks if the workflow state contains a value for the specified key.
     *
     * @param key The key to check for.
     * @return {@code true} if the state contains the key, {@code false} otherwise.
     */
    public boolean containsKey(String key) {
        return stateData.containsKey(key);
    }

    /**
     * Removes a key-value pair from the workflow state based on the key.
     *
     * @param key The key of the data to remove.
     * @return The value previously associated with the key, or {@code null} if the key was not found.
     */
    public Object remove(String key) {
        return stateData.remove(key);
    }


    /**
     * Returns an unmodifiable view of all data currently in the workflow state.
     * This is primarily for inspection or serialization. To modify the state,
     * use methods like {@link #put(String, Object)} or {@link #remove(String)}.
     *
     * @return An unmodifiable {@code Map<String, Object>} of the current state data.
     */
    public Map<String, Object> getAll() {
        return Collections.unmodifiableMap(stateData);
    }

    /**
     * Creates and returns a new {@code WorkflowState} instance that is a shallow copy
     * of the current state. The underlying map data is copied into a new {@code HashMap},
     * but the keys and values themselves are not deep-copied.
     *
     * <p>This is useful for:
     * <ul>
     *   <li>Snapshotting the state at a particular point.</li>
     *   <li>Providing a mutable copy of the state to a component (like a node) that might modify it,
     *       without affecting the original state object if it's being held elsewhere.</li>
     * </ul>
     * </p>
     * @return A new {@code WorkflowState} instance containing a copy of the current state data.
     */
    public WorkflowState copy() {
        return new WorkflowState(new HashMap<>(this.stateData)); // Pass a new HashMap copy
    }

    @Override
    public String toString() {
        return "WorkflowState{" +
                "stateData_keys=" + stateData.keySet() + // Avoid printing potentially large values
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowState that = (WorkflowState) o;
        return Objects.equals(stateData, that.stateData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stateData);
    }
}

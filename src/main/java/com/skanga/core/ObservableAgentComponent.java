package com.skanga.core;

/**
 * Interface for components that can be observed by {@link AgentObserver}s.
 * This allows various parts of the system (like Agents, Workflows, etc.)
 * to emit events without being tightly coupled to specific observer implementations.
 *
 * Components implementing this interface are expected to manage a list of observers
 * and notify them appropriately. The {@link ObservableSupport} class provides a
 * helper implementation for this.
 */
public interface ObservableAgentComponent {

    /**
     * Adds an observer to this component.
     * The observer will be notified of events that match the provided eventFilter.
     *
     * @param observer    The {@link AgentObserver} to add. Must not be null.
     * @param eventFilter A string to filter events. This can be an event type name (case-insensitive),
     *                    a wildcard ("*") for all events, or potentially a more complex filter string
     *                    if the concrete implementation supports it. If null, it might default to "*".
     */
    void addObserver(AgentObserver observer, String eventFilter);

    /**
     * Removes a previously added observer.
     * If the observer was added multiple times with different filters, this typically
     * removes all occurrences of that specific observer instance.
     *
     * @param observer The {@link AgentObserver} to remove. Must not be null.
     */
    void removeObserver(AgentObserver observer);

    /**
     * Notifies all registered observers whose event filter matches the given eventType.
     *
     * @param eventType The type of the event (e.g., "workflow-start", "node-error").
     *                  This string is usually matched case-insensitively against filters.
     *                  Should not be null or empty.
     * @param eventData The data associated with the event. Can be any object, or null.
     */
    void notifyObservers(String eventType, Object eventData);
}

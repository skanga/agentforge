package com.skanga.core;

/**
 * Defines an observer for agent-related events.
 * Implementations of this interface can be registered with an {@link Agent}
 * or other {@link ObservableAgentComponent} to receive notifications about
 * various lifecycle events or actions occurring within the agent or component.
 *
 * This allows for decoupling of concerns like logging, monitoring, or other
 * reactions to agent behavior.
 */
@FunctionalInterface // Good candidate for functional interface if only one method
public interface AgentObserver {

    /**
     * This method is called by an {@link ObservableAgentComponent} when an event occurs.
     *
     * @param eventType A string identifying the type of event (e.g., "chat-start", "tool-called", "error").
     *                  Observers can use this to filter or dispatch handling for specific events.
     * @param eventData An object containing data associated with the event. The type and structure
     *                  of this data depend on the specific `eventType`. Observers may need to
     *                  cast this object or inspect its type to extract relevant information.
     */
    void update(String eventType, Object eventData);
}

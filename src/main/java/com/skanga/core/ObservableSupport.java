package com.skanga.core;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A helper class that provides a basic implementation of the {@link ObservableAgentComponent} interface.
 * Components can delegate their observability methods to an instance of this class
 * to simplify the management and notification of {@link AgentObserver}s.
 *
 * This implementation is thread-safe for concurrent reads (notifications) and
 * writes (add/remove observer), making it suitable for scenarios where observers might be added or removed while
 * notifications are occurring.
 */
public class ObservableSupport implements ObservableAgentComponent {

    // Using CopyOnWriteArrayList for thread-safe operations
    private final List<ObserverMapping> observers = new CopyOnWriteArrayList<>();

    // Counter to track when cleanup should occur
    private volatile int operationCount = 0;
    private static final int CLEANUP_THRESHOLD = 100;

    /**
     * The source object that is emitting the events.
     * Used for logging/debugging purposes to identify the origin of notifications.
     */
    private final Object source;

    /**
     * Constructs an ObservableSupport instance.
     * @param source The object that will be emitting events via this support class. Must not be null.
     *               This helps in identifying the source of logs or errors from observers.
     */
    public ObservableSupport(Object source) {
        this.source = Objects.requireNonNull(source, "Observable source cannot be null.");
    }

    /**
     * Constructs an ObservableSupport instance where the source is this ObservableSupport instance itself.
     * This is less common; usually, a distinct component delegates to ObservableSupport.
     */
    public ObservableSupport() {
        this.source = this;
    }

    /**
     * Inner class to hold an observer and its associated event filter.
     */
    private static class ObserverMapping {
        private final WeakReference<AgentObserver> observerRef;
        private final String eventFilter; // Can be specific event type (case-insensitive) or "*" for all

        ObserverMapping(AgentObserver observer, String eventFilter) {
            this.observerRef = new WeakReference<>(Objects.requireNonNull(observer, "Observer cannot be null"));
            // Normalize filter: lowercase, default to "*" if null or empty for broader compatibility.
            this.eventFilter = (eventFilter == null || eventFilter.trim().isEmpty()) ? "*" : eventFilter.trim().toLowerCase();
        }

        /**
         * Checks if this mapping's filter matches the given event type.
         * @param eventType The type of the event, compared case-insensitively.
         * @return True if it matches, false otherwise.
         */
        boolean matches(String eventType) {
            if (eventType == null) return false;
            return "*".equals(eventFilter) || eventFilter.equals(eventType.toLowerCase());
        }

        /**
         * Gets the observer if it's still alive.
         * @return The observer or null if it has been garbage collected.
         */
        AgentObserver getObserver() {
            return observerRef.get();
        }

        /**
         * Checks if the observer reference is still valid.
         * @return True if the observer is still alive, false otherwise.
         */
        boolean isValid() {
            return observerRef.get() != null;
        }
    }

    @Override
    public void addObserver(AgentObserver observer, String eventFilter) {
        Objects.requireNonNull(observer, "Observer to add cannot be null.");

        // Remove any existing mapping for this observer to prevent duplicates
        removeObserver(observer);

        // Add the new mapping
        observers.add(new ObserverMapping(observer, eventFilter));

        // Periodic cleanup
        if (++operationCount % CLEANUP_THRESHOLD == 0) {
            cleanupStaleReferences();
        }
    }

    @Override
    public void notifyObservers(String eventType, Object eventData) {
        if (eventType == null || eventType.trim().isEmpty()) {
            System.err.println("ObservableSupport: Event type cannot be null or empty when notifying observers. Source: " +
                    (source != null ? source.getClass().getSimpleName() : "unknown"));
            return;
        }

        // Create a snapshot for safe iteration (CopyOnWriteArrayList handles this efficiently)
        for (ObserverMapping mapping : observers) {
            AgentObserver observer = mapping.getObserver();

            // Skip if observer has been garbage collected
            if (observer == null) {
                continue;
            }

            try {
                if (mapping.matches(eventType)) {
                    observer.update(eventType, eventData);
                }
            } catch (Exception e) {
                // Log exceptions from observers to prevent one failing observer from stopping others.
                System.err.println("ObservableSupport: Exception in observer '" + observer.getClass().getName() +
                        "' for event '" + eventType + "' from source '" +
                        (source != null ? source.getClass().getSimpleName() : "unknown") + "': " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }

        // Periodic cleanup
        if (++operationCount % CLEANUP_THRESHOLD == 0) {
            cleanupStaleReferences();
        }
    }

    /**
     * Removes stale weak references where the observer has been garbage collected.
     * This method is thread-safe due to CopyOnWriteArrayList.
     */
    private void cleanupStaleReferences() {
        observers.removeIf(mapping -> !mapping.isValid());
    }

    @Override
    public void removeObserver(AgentObserver observer) {
        Objects.requireNonNull(observer, "Observer to remove cannot be null.");
        observers.removeIf(mapping -> {
            AgentObserver mappingObserver = mapping.getObserver();
            return mappingObserver == null || Objects.equals(mappingObserver, observer);
        });
    }

    /**
     * Clears all registered observers from this support instance.
     */
    public void removeAllObservers() {
        observers.clear();
    }

    /**
     * Gets the number of currently registered observers.
     * This performs cleanup to ensure an accurate count.
     * @return The count of active observers.
     */
    public int getObserverCount() {
        cleanupStaleReferences();
        return observers.size();
    }
}

package com.skanga.chat.history;

import com.skanga.chat.messages.Message;

/**
 * An in-memory implementation of {@link ChatHistory}.
 * Messages are stored in a simple list in RAM and are lost when the application stops
 * or the instance is garbage collected.
 *
 * This implementation is suitable for:
 * - Short-lived conversations.
 * - Scenarios where persistence is not required.
 * - Testing and development.
 *
 * It relies on {@link AbstractChatHistory} for common logic like context window management
 * (by message count in the abstract class's default implementation).
 */
public class InMemoryChatHistory extends AbstractChatHistory {

    /**
     * Default context window size if not specified.
     */
    private static final int DEFAULT_IN_MEMORY_CONTEXT_WINDOW = 100; // Example default

    /**
     * Constructs an InMemoryChatHistory with a specified context window size.
     *
     * @param contextWindow The maximum number of messages to retain in history.
     *                      Older messages will be truncated if this limit is exceeded.
     */
    public InMemoryChatHistory(int contextWindow) {
        super(contextWindow);
    }

    /**
     * Constructs an InMemoryChatHistory with a default context window size.
     * @see #DEFAULT_IN_MEMORY_CONTEXT_WINDOW
     */
    public InMemoryChatHistory() {
        this(DEFAULT_IN_MEMORY_CONTEXT_WINDOW);
    }


    /**
     * {@inheritDoc}
     * For InMemoryChatHistory, this method is a no-op as messages are already added
     * to the in-memory list by the superclass ({@link AbstractChatHistory#addMessage(Message)}).
     * No separate persistence step is needed.
     */
    @Override
    protected void storeMessage(Message message) {
        // No operation needed for in-memory store.
        // The 'history' list in AbstractChatHistory is the direct store.
    }

    /**
     * {@inheritDoc}
     * For InMemoryChatHistory, this method is a no-op as the in-memory list
     * is already cleared by the superclass ({@link AbstractChatHistory#flushAll()}).
     * No separate persistence clearing is needed.
     */
    @Override
    protected void clear() {
        // No operation needed for in-memory store.
        // The 'history' list in AbstractChatHistory is cleared directly.
    }

    // removeOldestMessage() is handled by AbstractChatHistory's direct list manipulation.
    // If AbstractChatHistory.removeOldestMessage() were abstract, an implementation
    // would be needed here, e.g.:
    /*
    @Override
    public void removeOldestMessage() {
        if (!this.history.isEmpty()) {
            this.history.remove(0); // Accessing protected 'history' list from superclass
        }
    }
    */
}

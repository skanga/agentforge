package com.skanga.chat.history;

import com.skanga.chat.messages.Message; // Using the new Message class

import java.util.List;
import java.util.Map;

/**
 * Interface for managing the history of messages in a conversation.
 * Implementations can range from simple in-memory stores to persistent file-based
 * or database-backed histories.
 */
public interface ChatHistory {

    /**
     * Adds a message to the chat history.
     *
     * @param message The {@link Message} to add.
     *                Implementations should handle storage and potential context window management.
     */
    void addMessage(Message message);

    /**
     * Retrieves all messages currently in the chat history.
     * The order should typically be chronological.
     *
     * @return A {@link List} of {@link Message} objects.
     *         This list might be a copy or an unmodifiable view depending on the implementation.
     */
    List<Message> getMessages();

    /**
     * Retrieves the last message added to the chat history.
     *
     * @return The last {@link Message}, or null if the history is empty.
     */
    Message getLastMessage();

    /**
     * Removes the oldest message(s) from the history.
     * This is often used for managing context window size, though the exact
     * mechanism (e.g., how many messages to remove) can be implementation-specific.
     */
    void removeOldestMessage();

    /**
     * Clears all messages from the chat history.
     * This also typically clears any persisted state if applicable.
     */
    void flushAll();

    /**
     * Calculates the total token usage based on the messages stored in the history.
     * This requires messages to have their {@link com.skanga.core.Usage} information populated.
     *
     * @return The sum of total tokens from all messages that have usage information.
     *         The interpretation (e.g., sum of `totalTokens` or just `completionTokens`)
     *         might vary based on specific needs, but typically it's `totalTokens`.
     */
    int calculateTotalUsage();

    /**
     * Converts the chat history into a list of maps, suitable for JSON serialization.
     * This is useful for persistence or for debugging.
     * Concrete implementations might rely on Jackson directly for serialization of `List<Message>`
     * if the `Message` objects (and their content) are Jackson-compatible.
     *
     * @return A list of maps, where each map represents a message.
     *         Returns an empty list if the history is empty.
     */
    List<Map<String, Object>> toJsonSerializable();
}

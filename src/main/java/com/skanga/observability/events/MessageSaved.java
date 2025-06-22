package com.skanga.observability.events;

import com.skanga.chat.messages.Message;
import com.skanga.chat.history.ChatHistory;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Event data for when a {@link Message} has been successfully saved to a {@link ChatHistory}.
 * This event is triggered after the message has been added to the history store.
 *
 * @param message     The {@link Message} that was saved. Must not be null.
 * @param chatHistoryIdentifier A string identifier for the chat history instance where the message was saved.
 *                              Typically the class name or a custom ID. Must not be null.
 * @param context     Optional map of key-value pairs providing additional context about the save operation.
 *                    Can be null or empty. A defensive copy is made.
 */
public record MessageSaved(
    Message message,
    String chatHistoryIdentifier,
    Map<String, Object> context
) {
    /**
     * Canonical constructor for MessageSaved.
     * Ensures message and chatHistoryIdentifier are not null.
     * Makes the context map unmodifiable.
     */
    public MessageSaved {
        Objects.requireNonNull(message, "Message cannot be null for MessageSaved event.");
        Objects.requireNonNull(chatHistoryIdentifier, "chatHistoryIdentifier cannot be null for MessageSaved event.");
        context = (context != null) ? Collections.unmodifiableMap(context) : Collections.emptyMap();
    }

    /**
     * Convenience constructor using the {@link ChatHistory} object directly to derive an identifier.
     * @param message The message that was saved.
     * @param chatHistory The chat history instance. If null, identifier becomes "null_chat_history".
     */
     public MessageSaved(Message message, ChatHistory chatHistory) {
        this(message,
             chatHistory != null ? chatHistory.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(chatHistory)) : "null_chat_history",
             null);
    }

    /**
     * Convenience constructor with a chat history identifier but no extra context.
     * @param message The message that was saved.
     * @param chatHistoryIdentifier Identifier for the chat history.
     */
    public MessageSaved(Message message, String chatHistoryIdentifier) {
        this(message, chatHistoryIdentifier, null);
    }
}

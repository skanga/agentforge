package com.skanga.observability.events;

import com.skanga.chat.messages.Message;
import com.skanga.chat.history.ChatHistory;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Event data for when a {@link Message} is about to be saved to a {@link ChatHistory}.
 * This event is triggered just before the message is actually added to the history store.
 * It can be used for logging, pre-save validation, or modification if the design allows.
 *
 * @param message     The {@link Message} that is being saved. Must not be null.
 * @param chatHistoryIdentifier A string identifier for the chat history instance where the message will be saved.
 *                              This is typically the class name of the chat history implementation
 *                              (e.g., "InMemoryChatHistory", "FileChatHistory") or a custom ID if available.
 *                              Must not be null.
 * @param context     Optional map of key-value pairs providing additional context about the save operation.
 *                    Can be null or empty. A defensive copy is made.
 */
public record MessageSaving(
    Message message,
    String chatHistoryIdentifier,
    Map<String, Object> context
) {
    /**
     * Canonical constructor for MessageSaving.
     * Ensures message and chatHistoryIdentifier are not null.
     * Makes the context map unmodifiable.
     */
    public MessageSaving {
        Objects.requireNonNull(message, "Message cannot be null for MessageSaving event.");
        Objects.requireNonNull(chatHistoryIdentifier, "chatHistoryIdentifier cannot be null for MessageSaving event.");
        context = (context != null) ? Collections.unmodifiableMap(context) : Collections.emptyMap();
    }

    /**
     * Convenience constructor using the {@link ChatHistory} object directly to derive an identifier.
     * @param message The message being saved.
     * @param chatHistory The chat history instance. If null, identifier becomes "null_chat_history".
     */
    public MessageSaving(Message message, ChatHistory chatHistory) {
        this(message,
             chatHistory != null ? chatHistory.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(chatHistory)) : "null_chat_history",
             null);
    }

    /**
     * Convenience constructor with a chat history identifier but no extra context.
     * @param message The message being saved.
     * @param chatHistoryIdentifier Identifier for the chat history.
     */
     public MessageSaving(Message message, String chatHistoryIdentifier) {
        this(message, chatHistoryIdentifier, null);
    }
}

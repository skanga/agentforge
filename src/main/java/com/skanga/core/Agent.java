package com.skanga.core;

import com.skanga.chat.history.ChatHistory;
import com.skanga.chat.messages.Message;
import com.skanga.core.messages.MessageRequest;
import com.skanga.providers.AIProvider;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Defines the core interface for an AI agent.
 * An agent can interact with AI providers, manage tools, maintain chat history,
 * and handle various types of interactions like chat, streaming, and structured output.
 * It also supports observability through {@link AgentObserver}.
 */
public interface Agent {
    /**
     * Sets the AI provider for this agent.
     * @param provider The {@link AIProvider} to use for AI model interactions.
     * @return The current agent instance for fluent chaining.
     */
    Agent withProvider(AIProvider provider);

    /**
     * Resolves and returns the currently configured AI provider.
     * @return The configured {@link AIProvider}.
     * @throws com.skanga.core.exceptions.AgentException if no provider is set.
     */
    AIProvider resolveProvider();

    /**
     * Sets the system-level instructions for the agent.
     * These instructions guide the AI model's behavior.
     * @param instructions The system instructions string.
     * @return The current agent instance for fluent chaining.
     */
    Agent withInstructions(String instructions);

    /**
     * Gets the current system-level instructions for the agent.
     * Renamed from `instructions()` in PHP for Java bean convention.
     * @return The system instructions string.
     */
    String getInstructions();

    /**
     * Adds a tool to the agent's capabilities.
     * The nature of the 'tool' object is generic here and will be refined
     * by concrete implementations or a specific Tool/Toolkit interface.
     * @param tool The tool object to add.
     * @return The current agent instance for fluent chaining.
     */
    Agent addTool(Object tool);

    /**
     * Gets the list of tools available to the agent.
     * @return A list of tool objects.
     */
    List<Object> getTools();

    /**
     * Sets the chat history manager for this agent.
     * @param chatHistory The {@link ChatHistory} instance to use.
     * @return The current agent instance for fluent chaining.
     */
    Agent withChatHistory(ChatHistory chatHistory);

    /**
     * Resolves and returns the currently configured chat history manager.
     * @return The configured {@link ChatHistory}.
     * @throws com.skanga.core.exceptions.AgentException if no chat history is set.
     */
    ChatHistory resolveChatHistory();

    /**
     * Adds an observer to listen to agent events.
     * @param observer The {@link AgentObserver} to add.
     * @param event    A filter for which events the observer should receive (e.g., event name or "*").
     */
    void addObserver(AgentObserver observer, String event);

    /**
     * Notifies registered observers about an agent event.
     * @param eventType The type/name of the event.
     * @param data      The data associated with the event.
     */
    void notifyObservers(String eventType, Object data);

    /**
     * Performs a synchronous chat interaction.
     * This typically involves sending a {@link MessageRequest} (which wraps one or more messages)
     * to the AI provider and receiving a single {@link Message} response.
     * @param messages The {@link MessageRequest} containing the input messages.
     * @return The {@link Message} response from the AI.
     */
    Message chat(MessageRequest messages);

    /**
     * Performs an asynchronous chat interaction.
     * @param messages The {@link MessageRequest} containing the input messages.
     * @return A {@link CompletableFuture} that will complete with the {@link Message} response from the AI.
     */
    CompletableFuture<Message> chatAsync(MessageRequest messages);

    /**
     * Initiates a streaming interaction with the AI provider.
     * This is used for receiving responses as a stream of text chunks.
     * @param messages The {@link MessageRequest} containing the input messages.
     * @return A {@link Stream} of {@link String} chunks representing the AI's response.
     */
    Stream<String> stream(MessageRequest messages);

    /**
     * Performs a structured interaction, aiming to get a response from the AI
     * that conforms to a specific class structure (e.g., a POJO or record).
     *
     * @param messages       The {@link MessageRequest} containing the input messages.
     * @param responseClass  The {@link Class} of the desired structured response type.
     * @param maxRetries     The maximum number of retries if parsing or validation fails.
     * @param <T>            The type of the structured response.
     * @return An instance of {@code responseClass} (type T) populated with data from the AI.
     */
    <T> T structured(MessageRequest messages, Class<T> responseClass, int maxRetries);

    void removeObserver(AgentObserver observer);
    void removeAllObservers();
}

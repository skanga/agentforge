package com.skanga.core.messages;

import java.util.List;
import java.util.Objects;

/**
 * Represents a message from the AI model requesting one or more tool calls.
 * This is typically used as the content of an {@link com.skanga.chat.messages.AssistantMessage}.
 *
 * The structure aims to be compatible with common LLM provider formats (e.g., OpenAI).
 *
 * @param id An optional identifier for the overall set of tool call requests in this message.
 *           May not always be provided by the LLM.
 * @param toolCalls A list of specific {@link ToolCall} instances requested by the AI.
 *                  This list should not be null; it can be empty if no tools are called.
 */
public record ToolCallMessage(
    String id, // Assuming JSON 'id' maps to this
    @com.fasterxml.jackson.annotation.JsonProperty("tool_calls") List<ToolCall> toolCalls
) {
    /**
     * Canonical constructor.
     * Ensures that the list of tool calls is not null.
     */
    public ToolCallMessage {
        Objects.requireNonNull(toolCalls, "toolCalls list cannot be null.");
    }

    /**
     * Represents a single tool call requested by the AI model.
     *
     * @param id       A unique identifier for this specific tool call, provided by the LLM.
     *                 This ID is crucial for matching with a {@link ToolCallResultMessage}.
     * @param type     The type of the tool being called. For now, typically "function".
     * @param function The details of the function to be called.
     */
    public record ToolCall(
        String id,
        String type,
        FunctionCall function
    ) {
        public ToolCall {
            Objects.requireNonNull(id, "ToolCall ID cannot be null.");
            Objects.requireNonNull(type, "ToolCall type cannot be null.");
            Objects.requireNonNull(function, "ToolCall function details cannot be null.");
        }
    }

    /**
     * Represents the function call details, including its name and arguments.
     *
     * @param name      The name of the function/tool to be invoked.
     * @param arguments A string containing the arguments for the function, typically in JSON format.
     *                  The tool itself will be responsible for parsing these arguments.
     */
    public record FunctionCall(
        String name,
        String arguments
    ) {
         public FunctionCall {
            Objects.requireNonNull(name, "FunctionCall name cannot be null.");
            Objects.requireNonNull(arguments, "FunctionCall arguments cannot be null (can be empty JSON string '{}').");
        }
    }
}

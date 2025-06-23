package com.skanga.core.messages;

import java.util.Objects;

/**
 * Represents the result of a single tool execution, to be sent back to the AI model.
 * This is typically used as the content of a {@link com.skanga.chat.messages.Message}
 * with the role {@link com.skanga.chat.enums.MessageRole#TOOL}.
 *
 * The structure aims for compatibility with common LLM provider formats (e.g., OpenAI).
 *
 * @param toolCallId The ID of the original tool call (from {@link ToolCallMessage.ToolCall#id()})
 *                   this result corresponds to. This is crucial for the AI to match results to requests.
 * @param role       The role for this message, which should always be "tool".
 *                   While the enum {@link com.skanga.chat.enums.MessageRole#TOOL} is used in the main
 *                   {@link com.skanga.chat.messages.Message}, this field is often required
 *                   as a string by providers when submitting tool results as part of their message structure.
 * @param name       The name of the tool that was called (corresponds to {@link ToolCallMessage.FunctionCall#name()}).
 *                   Required by some providers (like OpenAI) in the tool result message part.
 * @param content    The result of the tool execution, typically as a string (e.g., JSON string, raw text).
 */
public record ToolCallResultMessage(
    @com.fasterxml.jackson.annotation.JsonProperty("tool_call_id") String toolCallId,
    String role, // Default serialization will be "role"
    String name,   // Default serialization will be "name"
    String content
) {
    /**
     * Canonical constructor for ToolCallResultMessage.
     * Ensures all fields are non-null.
     */
    public ToolCallResultMessage {
        Objects.requireNonNull(toolCallId, "toolCallId cannot be null");
        Objects.requireNonNull(role, "role cannot be null (should be 'tool')");
        Objects.requireNonNull(name, "name (tool name) cannot be null");
        Objects.requireNonNull(content, "content (tool result) cannot be null (can be empty string)");
        if (!"tool".equals(role)) {
            // While the Message object will have MessageRole.TOOL, some providers
            // expect the 'role' field within the tool message part to also be "tool".
            // This check is more of a semantic reminder.
            // System.err.println("Warning: ToolCallResultMessage role is typically 'tool', but received: " + role);
        }
    }

    /**
     * Convenience constructor that defaults the role to "tool".
     *
     * @param toolCallId The ID of the original tool call.
     * @param name       The name of the tool that was called.
     * @param content    The string content of the tool execution result.
     */
    public ToolCallResultMessage(String toolCallId, String name, String content) {
        this(toolCallId, "tool", name, content);
    }
}

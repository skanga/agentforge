package com.skanga.chat.enums;

/**
 * Defines the possible roles of a sender in a chat message.
 * This is crucial for structuring conversations for AI models, as they often
 * expect distinct roles for user input, assistant responses, system instructions, etc.
 */
public enum MessageRole {
    /**
     * Message from the end-user.
     */
    USER,

    /**
     * Message from the AI assistant or model.
     * This is typically the role for normal text responses or when the AI requests tool calls.
     */
    ASSISTANT,

    /**
     * Message from the AI model. Often used interchangeably with {@link #ASSISTANT},
     * but can be distinct in some systems or provider APIs (e.g., Gemini uses "model").
     * In this system, {@link com.skanga.chat.messages.AssistantMessage} uses ASSISTANT.
     * Providers might map this to "model" or "assistant" as needed.
     */
    MODEL,

    /**
     * Message generated as a result of a tool (function) execution.
     * This role is used to feed the output of a tool back to the AI model.
     */
    TOOL,

    /**
     * System-level instructions or context provided to the AI model.
     * This typically guides the model's behavior, personality, or response format.
     */
    SYSTEM,

    /**
     * Messages inserted by a developer, often for debugging, testing, or providing
     * specific out-of-band instructions or context during development.
     * May not be directly supported or recognized by all AI providers.
     */
    DEVELOPER;
}

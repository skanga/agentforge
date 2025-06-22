package com.skanga.chat.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.skanga.chat.enums.MessageRole;
import com.skanga.core.Usage; // For constructor including Usage

/**
 * Represents a message originating from an AI assistant or model.
 * Extends the base {@link Message} class, automatically setting the role to {@link MessageRole#ASSISTANT}.
 * This message type can contain simple text content, or more complex content like
 * a {@link com.skanga.core.messages.ToolCallMessage} if the assistant is requesting tool executions.
 */
public class AssistantMessage extends Message {

    /**
     * Default constructor, primarily for Jackson deserialization or manual role setting.
     * It's generally recommended to use constructors that take content.
     * This constructor sets the role to {@link MessageRole#ASSISTANT}.
     */
    public AssistantMessage() {
        super(); // Calls Message() default constructor
        this.role = MessageRole.ASSISTANT;
    }

    /**
     * Constructs a new AssistantMessage with the specified content.
     * The role is automatically set to {@link MessageRole#ASSISTANT}.
     *
     * @param content The content of the assistant's message. This can be a String (text response)
     *                or an object like {@link com.skanga.core.messages.ToolCallMessage}
     *                if the assistant is delegating tasks to tools.
     */
    @JsonCreator // Useful if this is the primary way to create via Jackson
    public AssistantMessage(@JsonProperty("content") Object content) {
        // Calls Message(MessageRole, Object) constructor
        super(MessageRole.ASSISTANT, content);
    }

    /**
     * Constructs a new AssistantMessage with specified content and usage information.
     * The role is automatically set to {@link MessageRole#ASSISTANT}.
     *
     * @param content The content of the assistant's message.
     * @param usage Token {@link Usage} information for this message, typically provided by the LLM.
     */
    public AssistantMessage(Object content, Usage usage) {
        // Calls Message(MessageRole, Object, Usage) constructor
        super(MessageRole.ASSISTANT, content, usage);
    }

    // If AssistantMessage needs to specifically handle attachments or metadata differently,
    // those methods can be overridden here. For now, it inherits Message's behavior.
}

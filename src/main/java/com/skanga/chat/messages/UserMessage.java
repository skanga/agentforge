package com.skanga.chat.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.skanga.chat.enums.MessageRole;
// Usage is not typically set directly on UserMessages by the user,
// but could be associated if the system echoes or processes them.
// For now, constructors don't include Usage.

/**
 * Represents a message originating from a user.
 * Extends the base {@link Message} class, automatically setting the role to {@link MessageRole#USER}.
 */
public class UserMessage extends Message {

    /**
     * Default constructor, primarily for Jackson deserialization or manual role setting.
     * It's generally recommended to use the constructor that takes content.
     * This constructor sets the role to {@link MessageRole#USER}.
     */
    public UserMessage() {
        super(); // Calls Message() default constructor
        this.role = MessageRole.USER;
    }

    /**
     * Constructs a new UserMessage with the specified content.
     * The role is automatically set to {@link MessageRole#USER}.
     *
     * @param content The content of the user's message. This is typically a String,
     *                but can be other objects if the system supports complex user inputs
     *                (though less common for user messages than assistant/tool messages).
     *                For {@link com.skanga.core.messages.ToolCallResultMessage}, use a `Message` with `MessageRole.TOOL`.
     */
    @JsonCreator // Useful if this is the primary way to create via Jackson, along with default constructor
    public UserMessage(@JsonProperty("content") Object content) {
        // Calls Message(MessageRole, Object) constructor
        super(MessageRole.USER, content);
    }

    // If UserMessage needs to specifically handle attachments or metadata differently,
    // those methods can be overridden here. For now, it inherits Message's behavior.
}

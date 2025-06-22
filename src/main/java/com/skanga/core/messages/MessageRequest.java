package com.skanga.core.messages;

// import java.util.List; // No longer needed directly if Message is from another package
// import com.skanga.core.messages.Message; // Old Message import
import java.util.List;


// A MessageRequest can be a single message (e.g. for a simple prompt)
// or a list of messages (e.g. for chat history).
// Using a List<Message> for flexibility.
public record MessageRequest(List<com.skanga.chat.messages.Message> messages) { // Use new Message
    // Convenience constructor for a single message
    public MessageRequest(com.skanga.chat.messages.Message message) { // Use new Message
        this(List.of(message));
    }

    // Convenience constructor for a single message with role and content
    // This constructor needs to use the new MessageRole enum and the new Message class.
    // This might be better handled by the caller creating the Message instance.
    // For now, let's adapt it, assuming MessageRole can be found or passed.
    // This is tricky because MessageRole is in com.skanga.chat.enums
    // If Message constructor Message(role, content) requires MessageRole enum,
    // then this constructor needs it.
    // public MessageRequest(String role, String content) { // Old signature
    // This constructor is problematic if `role` is a String and Message expects `MessageRole`.
    // It's better to construct Message outside and pass it.
    // For now, I will comment it out to avoid an immediate compilation error due to MessageRole.
    // The caller should use new MessageRequest(new Message(MessageRole.USER, "hello")) for instance.
    /*
    public MessageRequest(String role, String content) {
        this(new Message(role, content)); // This line would fail if Message expects MessageRole
    }
    */
}

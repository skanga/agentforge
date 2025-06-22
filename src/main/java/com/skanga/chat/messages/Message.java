package com.skanga.chat.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.skanga.chat.attachments.Attachment;
import com.skanga.chat.enums.MessageRole;
import com.skanga.core.Usage; // Assuming Usage remains in core

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single message in a conversation or interaction.
 * This class is designed to be flexible, allowing for various types of content,
 * attachments, and metadata.
 *
 * Key features:
 * - Role-based messages (user, assistant, tool, system).
 * - Content can be various types (so we use Object instead of String). For specific structured
 *   content like tool calls or results, the `content` field would hold specific objects
 *   (e.g., {@link com.skanga.core.messages.ToolCallMessage} or
 *   {@link com.skanga.core.messages.ToolCallResultMessage}).
 * - Support for attachments and metadata.
 * - Usage tracking (tokens).
 *
 * Potential areas for future refinement:
 * - Counting Tokens: Token counting is usually provider-specific & would need a separate utility.
 * - Mutability: This class is mutable (setters) to allow for progressive building or updates.
 *   Consider if immutable builders or records for specific message types would be better.
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // Don't serialize null fields
public class Message {

    /** The role of the entity sending the message (e.g., user, assistant). */
    @JsonProperty("role")
    protected MessageRole role;

    /**
     * The content of the message. Can be a simple String (text),
     * or a complex object like {@link com.skanga.core.messages.ToolCallMessage}
     * or {@link com.skanga.core.messages.ToolCallResultMessage}.
     */
    @JsonProperty("content")
    protected Object content;

    /** Token usage information associated with this message, typically for LLM responses. */
    @JsonProperty("usage")
    protected Usage usage;

    /** List of attachments associated with this message (e.g., images, documents). */
    @JsonProperty("attachments")
    protected List<Attachment> attachments = new ArrayList<>();

    /** Additional metadata associated with the message. */
    @JsonProperty("meta")
    protected Map<String, Object> meta = new HashMap<>();

    /**
     * Default constructor, primarily for Jackson deserialization.
     * It's recommended to use constructors with role and content.
     */
    public Message() {}

    /**
     * Constructs a new Message with a specified role and content.
     * @param role The {@link MessageRole} of the message sender.
     * @param content The content of the message. Can be String or complex objects like ToolCallMessage.
     */
    @JsonCreator // Indicate this can be used by Jackson for deserialization if multiple constructors exist
    public Message(@JsonProperty("role") MessageRole role, @JsonProperty("content") Object content) {
        this.role = Objects.requireNonNull(role, "Message role cannot be null.");
        this.content = content; // Content can be null (e.g., assistant message requesting tool calls might have null content)
    }

    /**
     * Constructs a new Message with role, content, and usage information.
     * @param role The {@link MessageRole} of the message sender.
     * @param content The content of the message.
     * @param usage Token {@link Usage} information for this message.
     */
    public Message(MessageRole role, Object content, Usage usage) {
        this(role, content);
        this.usage = usage;
    }

    /**
     * Gets the role of the message sender.
     * @return The {@link MessageRole}.
     */
    public MessageRole getRole() {
        return role;
    }

    /**
     * Sets the role of the message sender.
     * @param role The {@link MessageRole}.
     */
    public void setRole(MessageRole role) {
        this.role = Objects.requireNonNull(role, "Message role cannot be null.");
    }

    /**
     * Gets the content of the message.
     * This can be a simple String or a more complex object (e.g., {@link ToolCallMessage}).
     * Callers may need to perform `instanceof` checks and casts.
     * @return The message content.
     */
    public Object getContent() {
        return content;
    }

    /**
     * Sets the content of the message.
     * @param content The message content.
     */
    public void setContent(Object content) {
        this.content = content;
    }

    /**
     * Gets the token usage information for this message.
     * @return The {@link Usage} object, or null if not set.
     */
    public Usage getUsage() {
        return usage;
    }

    /**
     * Sets the token usage information for this message.
     * @param usage The {@link Usage} object.
     */
    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    /**
     * Gets the list of attachments for this message.
     * Returns an empty list if no attachments are present.
     * @return A list of {@link Attachment}s.
     */
    public List<Attachment> getAttachments() {
        return attachments;
    }

    /**
     * Sets the list of attachments for this message.
     * @param attachments A list of {@link Attachment}s.
     */
    public void setAttachments(List<Attachment> attachments) {
        this.attachments = (attachments == null) ? new ArrayList<>() : attachments;
    }

    /**
     * Adds an attachment to this message.
     * @param attachment The {@link Attachment} to add. Must not be null.
     */
    public void addAttachment(Attachment attachment) {
        Objects.requireNonNull(attachment, "Attachment cannot be null.");
        if (this.attachments == null) { // Should be initialized, but defensive
            this.attachments = new ArrayList<>();
        }
        this.attachments.add(attachment);
    }

    /**
     * Gets the metadata map associated with this message.
     * Returns an empty map if no metadata is present.
     * @return A map of metadata.
     */
    public Map<String, Object> getMeta() {
        return meta;
    }

    /**
     * Sets the metadata map for this message.
     * @param meta A map of metadata.
     */
    public void setMeta(Map<String, Object> meta) {
        this.meta = (meta == null) ? new HashMap<>() : meta;
    }

    /**
     * Adds a key-value pair to the message's metadata.
     * If the key already exists, its value will be overwritten.
     * @param key The metadata key. Must not be null.
     * @param value The metadata value.
     */
    public void addMetadata(String key, Object value) {
        Objects.requireNonNull(key, "Metadata key cannot be null.");
        if (this.meta == null) { // Should be initialized, but defensive
            this.meta = new HashMap<>();
        }
        this.meta.put(key, value);
    }

    @Override
    public String toString() {
        // Basic toString, avoiding overly long content string.
        String contentStr;
        if (content == null) {
            contentStr = "null";
        } else if (content instanceof String) {
            String s = (String) content;
            contentStr = "\"" + (s.length() > 50 ? s.substring(0, 47) + "..." : s) + "\"";
        } else {
            contentStr = content.getClass().getSimpleName();
        }

        return "Message{" +
                "role=" + role +
                ", content=" + contentStr +
                (usage != null ? ", usage=" + usage : "") +
                (attachments != null && !attachments.isEmpty() ? ", attachments_count=" + attachments.size() : "") +
                (meta != null && !meta.isEmpty() ? ", meta_keys=" + meta.keySet() : "") +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return role == message.role &&
               Objects.equals(content, message.content) &&
               Objects.equals(usage, message.usage) &&
               Objects.equals(attachments, message.attachments) &&
               Objects.equals(meta, message.meta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, content, usage, attachments, meta);
    }
}

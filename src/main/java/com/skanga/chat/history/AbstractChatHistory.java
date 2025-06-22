package com.skanga.chat.history;

import com.skanga.chat.attachments.Attachment;
import com.skanga.chat.attachments.Document; // RAG Document
import com.skanga.chat.attachments.Image;
import com.skanga.chat.enums.AttachmentContentType;
import com.skanga.chat.enums.AttachmentType;
import com.skanga.chat.enums.MessageRole;
import com.skanga.chat.messages.AssistantMessage;
import com.skanga.chat.messages.Message;
import com.skanga.chat.messages.UserMessage;
import com.skanga.core.Usage;
import com.skanga.core.messages.ToolCallMessage;
import com.skanga.core.messages.ToolCallResultMessage;
import com.fasterxml.jackson.core.type.TypeReference; // For deserializing maps with generics
import com.fasterxml.jackson.databind.ObjectMapper; // For deserialization from map

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Abstract base class for {@link ChatHistory} implementations.
 * Provides common functionalities like managing an in-memory list of messages,
 * context window limiting (by message count or token count - currently simplified to message count),
 * and basic serialization/deserialization logic.
 *
 * Notes:
 * - Context Window: Instead of a token-based `cutHistoryToContextWindow` and `getFreeMemory`,
 *   this implementation uses message count for `cutHistoryToContextWindow`.
 *   Token-based limiting would require robust token counting for each message.
 * - Deserialization: The `deserializeMessages` and related helpers attempt to reconstruct specific
 *   message types (UserMessage, AssistantMessage) and complex content (ToolCallMessage,
 *   ToolCallResultMessage) from maps. This logic can be complex due_to `Object content` in
 *   {@link Message}. It assumes specific map structures
 *   created during serialization (e.g., by `toJsonSerializableMap`).
 *   Using Jackson for direct serialization/deserialization of `List<Message>` with
 *   properly annotated DTOs/polymorphic handling (`@JsonTypeInfo`) would be a more robust
 *   Java-idiomatic approach for persistence than manual map-based deserialization.
 */
public abstract class AbstractChatHistory implements ChatHistory {

    /** The list of messages forming the history. */
    protected List<Message> history = new ArrayList<>();
    /**
     * Defines the maximum context window size.
     * Interpretation (e.g., number of messages or number of tokens) is up to concrete implementations
     * of {@link #cutHistoryToContextWindow()}. This abstract class's version assumes message count.
     */
    protected final int contextWindow;

    /** For deserializing Map to complex objects like ToolCallMessage if stored as such. */
    private transient ObjectMapper internalObjectMapper = new ObjectMapper();


    /**
     * Constructs an AbstractChatHistory.
     * @param contextWindow The maximum size of the context window (e.g., number of messages).
     */
    public AbstractChatHistory(int contextWindow) {
        if (contextWindow <= 0) {
            throw new IllegalArgumentException("Context window size must be positive.");
        }
        this.contextWindow = contextWindow;
    }

    @Override
    public void addMessage(Message message) {
        Objects.requireNonNull(message, "Message to add cannot be null.");
        // TODO: Usage is on Message. We need to update Used Tokens
        this.history.add(message);
        storeMessage(message); // Hook for persistence
        cutHistoryToContextWindow();
    }

    @Override
    public List<Message> getMessages() {
        return new ArrayList<>(this.history); // Return a copy for external use
    }

    @Override
    public Message getLastMessage() {
        return this.history.isEmpty() ? null : this.history.get(this.history.size() - 1);
    }

    @Override
    public void flushAll() {
        this.history.clear();
        clear(); // Hook for persistence
    }

    @Override
    public int calculateTotalUsage() {
        return this.history.stream()
            .map(Message::getUsage)
            .filter(Objects::nonNull)
            .mapToInt(Usage::totalTokens)
            .sum();
    }

    @Override
    public void removeOldestMessage() {
        if (!this.history.isEmpty()) {
            this.history.remove(0);
            // Persistence for removing the oldest message should be handled by implementing classes
            // if they store messages individually. storeMessage/clear are for full state.
            // This method primarily affects the in-memory list here.
            // Concrete persistent stores might need to override or have `storeMessage` / `clear`
            // reflect the removal of specific messages if not rewriting the whole history on each change.
        }
    }

    /**
     * Trims the history to maintain the configured context window size.
     * This implementation assumes `contextWindow` is a message count.
     * Subclasses can override for token-based truncation.
     */
    protected void cutHistoryToContextWindow() {
        // Simplified to message count instead of token counting.
        while (this.history.size() > this.contextWindow) {
            // removeOldestMessage() is called, which also handles in-memory list.
            // If a persistent store needs more specific "delete oldest" logic, it should override removeOldestMessage.
            this.removeOldestMessage();
        }
    }

    /**
     * Calculates available "free memory" in the context window.
     * Simplified to message count.
     * @return Number of additional messages that can be added before truncation.
     */
    public int getFreeMemory() {
        return Math.max(0, this.contextWindow - this.history.size());
    }

    @Override
    public List<Map<String, Object>> toJsonSerializable() {
        return this.history.stream()
            .map(this::serializeMessageToMap) // Convert each message to a map
            .collect(Collectors.toList());
    }

    /**
     * Converts a {@link Message} object to a {@code Map<String, Object>} for serialization.
     * This is a helper for {@link #toJsonSerializable()} and for persistence layers
     * that might store messages as JSON maps.
     *
     * @param message The message to serialize.
     * @return A map representation of the message.
     */
    protected Map<String, Object> serializeMessageToMap(Message message) {
        Map<String, Object> map = new HashMap<>();
        map.put("role", message.getRole().name()); // Store enum name

        // Content serialization:
        // If content is a known complex type (ToolCallMessage, etc.), it might be serialized
        // as a nested map. Otherwise, toString() or direct if primitive/String.
        // Jackson would handle this automatically if serializing Message object directly.
        Object content = message.getContent();
        if (content instanceof ToolCallMessage || content instanceof ToolCallResultMessage) {
            // Use Jackson to convert these specific records/objects to map for consistent structure
            map.put("content", internalObjectMapper.convertValue(content, new TypeReference<Map<String, Object>>() {}));
            // Add a type hint for these specific content objects for easier deserialization
            if (content instanceof ToolCallMessage) map.put("content_type", "ToolCallMessage");
            if (content instanceof ToolCallResultMessage) map.put("content_type", "ToolCallResultMessage");

        } else {
             map.put("content", content); // Store as is (String, Number, Boolean, or let Jackson handle others)
        }


        if (message.getUsage() != null) {
            map.put("usage", internalObjectMapper.convertValue(message.getUsage(), new TypeReference<Map<String, Object>>() {}));
        }
        if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
            map.put("attachments", message.getAttachments().stream()
                .map(this::serializeAttachmentToMap)
                .collect(Collectors.toList()));
        }
        if (message.getMeta() != null && !message.getMeta().isEmpty()) {
            map.put("meta", new HashMap<>(message.getMeta())); // Copy meta
        }

        // Add a "class_type" hint for specific Message subclasses if needed for deserialization
        if (message instanceof UserMessage) map.put("message_class_type", "UserMessage");
        else if (message instanceof AssistantMessage) map.put("message_class_type", "AssistantMessage");
        // else, it's a base Message or other subclass

        return map;
    }

    /**
     * Helper to serialize an {@link Attachment} to a map.
     */
    protected Map<String, Object> serializeAttachmentToMap(Attachment attachment) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", attachment.getType().name());
        map.put("content", attachment.getContent());
        map.put("contentType", attachment.getContentType().name());
        map.put("mediaType", attachment.getMediaType());
        // Add "attachment_class_type" if Document/Image have specific fields to retain
        if (attachment instanceof Document) map.put("attachment_class_type", "Document");
        else if (attachment instanceof Image) map.put("attachment_class_type", "Image");
        return map;
    }

    /**
     * Deserializes a list of maps (presumably from JSON) into a list of {@link Message} objects.
     * This is used by persistence layers like {@link FileChatHistory} to reconstruct history.
     *
     * @param messagesData List of maps, each representing a serialized message.
     * @return List of deserialized {@link Message} objects.
     */
    @SuppressWarnings("unchecked")
    protected List<Message> deserializeMessages(List<Map<String, Object>> messagesData) {
        List<Message> messages = new ArrayList<>();
        if (messagesData == null) return messages;

        for (Map<String, Object> messageData : messagesData) {
            messages.add(deserializeMessageFromMap(messageData));
        }
        this.history = messages; // Set the deserialized messages as the current history
        return messages;
    }

    /**
     * Deserializes a single map into a {@link Message} object.
     */
    @SuppressWarnings("unchecked")
    protected Message deserializeMessageFromMap(Map<String, Object> messageData) {
        MessageRole role = MessageRole.valueOf(((String) messageData.get("role")).toUpperCase());
        Object rawContent = messageData.get("content");
        String contentTypeHint = (String) messageData.get("content_type");
        String messageClassType = (String) messageData.get("message_class_type");

        Object content = rawContent;
        // Deserialize complex content types if hint is present
        if (contentTypeHint != null && rawContent instanceof Map) {
            Map<String, Object> contentMap = (Map<String, Object>) rawContent;
            if ("ToolCallMessage".equals(contentTypeHint)) {
                content = internalObjectMapper.convertValue(contentMap, ToolCallMessage.class);
            } else if ("ToolCallResultMessage".equals(contentTypeHint)) {
                content = internalObjectMapper.convertValue(contentMap, ToolCallResultMessage.class);
            }
        }

        Message messageInstance;
        if ("UserMessage".equals(messageClassType)) {
            messageInstance = new UserMessage(content);
        } else if ("AssistantMessage".equals(messageClassType)) {
            messageInstance = new AssistantMessage(content);
        } else {
            messageInstance = new Message(role, content); // Fallback to base Message
        }
        // Ensure role is correctly set if not handled by subclass constructor (it is for User/Assistant)
        if (messageInstance.getRole() != role) messageInstance.setRole(role);


        if (messageData.containsKey("usage")) {
            Map<String, Object> usageMap = (Map<String, Object>) messageData.get("usage");
            messageInstance.setUsage(internalObjectMapper.convertValue(usageMap, Usage.class));
        }

        if (messageData.containsKey("attachments")) {
            List<Map<String, Object>> attachmentsData = (List<Map<String, Object>>) messageData.get("attachments");
            for (Map<String, Object> attachmentData : attachmentsData) {
                messageInstance.addAttachment(deserializeAttachmentFromMap(attachmentData));
            }
        }

        if (messageData.containsKey("meta")) {
            messageInstance.setMeta((Map<String, Object>) messageData.get("meta"));
        }
        return messageInstance;
    }

    /**
     * Helper to deserialize an attachment map back to an {@link Attachment} object.
     */
    protected Attachment deserializeAttachmentFromMap(Map<String, Object> attachmentData) {
        AttachmentType attachType = AttachmentType.valueOf(((String) attachmentData.get("type")).toUpperCase());
        String attachContent = (String) attachmentData.get("content");
        AttachmentContentType attachContentType = AttachmentContentType.valueOf(((String) attachmentData.get("contentType")).toUpperCase());
        String mediaType = (String) attachmentData.get("mediaType");
        String attachmentClassType = (String) attachmentData.get("attachment_class_type");

        if ("Document".equals(attachmentClassType)) { // Assuming RAG Document is not meant here.
                                                     // This refers to chat.attachments.Document
            return new com.skanga.chat.attachments.Document(attachContent, attachContentType, mediaType);
        } else if ("Image".equals(attachmentClassType)) {
            return new Image(attachContent, attachContentType, mediaType);
        }
        // Fallback or throw error if type hint is missing or unknown
        // For now, creating a base Attachment if specific type not determined
        System.err.println("Warning: Deserializing attachment without specific class type hint, using base Attachment. Type: " + attachType);
        return new Attachment(attachType, attachContent, attachContentType, mediaType);
    }

    // --- Abstract methods for persistence hooks ---

    /**
     * Persists a single message. Called after a message is added to the in-memory history.
     * Concrete persistent stores (like FileChatHistory) should implement this to save the message.
     * In-memory stores might have an empty implementation if the list itself is the store.
     * @param message The message to store.
     */
    protected abstract void storeMessage(Message message);

    /**
     * Clears all messages from the persistent store. Called after the in-memory history is cleared.
     * Concrete persistent stores should implement this to delete all persisted messages.
     * In-memory stores might have an empty implementation.
     */
    protected abstract void clear();
}

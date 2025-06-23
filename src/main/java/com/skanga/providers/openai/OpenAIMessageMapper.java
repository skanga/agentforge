
// openai/OpenAIMessageMapper.java
package com.skanga.providers.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.chat.attachments.Attachment;
import com.skanga.chat.enums.AttachmentContentType;
import com.skanga.chat.enums.MessageRole;
import com.skanga.chat.messages.Message;
import com.skanga.core.exceptions.ProviderException;
import com.skanga.core.messages.ToolCallMessage;
import com.skanga.core.messages.ToolCallResultMessage;
import com.skanga.providers.mappers.MessageMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Maps generic {@link Message} objects to the specific format required by the OpenAI API.
 * This includes handling different message roles, content types (text, tool calls, tool results),
 * and attachments (like images for vision-capable models).
 */
public class OpenAIMessageMapper implements MessageMapper {
    private final ObjectMapper localObjectMapper = new ObjectMapper();

    @Override
    public List<Map<String, Object>> map(List<Message> messages) {
        Objects.requireNonNull(messages, "Input message list cannot be null.");
        return messages.stream()
            .map(this::mapMessageToOpenAIFormat)
            .filter(Objects::nonNull) // Filter out any null messages (e.g., unsupported roles if not throwing)
            .collect(Collectors.toList());
    }

    /**
     * Maps a single {@link Message} to the OpenAI API message format.
     *
     * @param message The generic message to map.
     * @return A map representing the message in OpenAI's format, or null if the role is unsupported.
     * @throws ProviderException if mapping fails for a supported role (e.g., invalid attachment type).
     */
    private Map<String, Object> mapMessageToOpenAIFormat(Message message) {
        String openAIRole = mapRoleToOpenAI(message.getRole());
        if (openAIRole == null) {
            // Optionally log a warning or skip if a role truly isn't mappable.
            // For now, strict mapping or throwing from mapRoleToOpenAI.
            // If mapRoleToOpenAI throws, this won't be reached. If it returns null, skip.
            System.err.println("Warning: Skipping message with unmappable role for OpenAI: " + message.getRole());
            return null;
        }

        Map<String, Object> mappedMessage = new HashMap<>();
        mappedMessage.put("role", openAIRole);

        Object content = message.getContent();
        List<Attachment> attachments = message.getAttachments();

        // Handle complex content types first (ToolCallMessage, ToolCallResultMessage)
        if (content instanceof ToolCallMessage) {
            // This is an Assistant message requesting tool calls
            ToolCallMessage tcMessage = (ToolCallMessage) content;
            List<Map<String, Object>> toolCallsPayload = new ArrayList<>();

            if (tcMessage.toolCalls() != null) {
                for (ToolCallMessage.ToolCall tc : tcMessage.toolCalls()) {
                    Map<String, Object> callMap = new HashMap<>();
                    callMap.put("id", tc.id());
                    callMap.put("type", tc.type()); // Should be "function"

                    Map<String, Object> functionMap = new HashMap<>();
                    functionMap.put("name", tc.function().name());
                    functionMap.put("arguments", tc.function().arguments()); // Arguments are already a JSON string
                    callMap.put("function", functionMap);
                    toolCallsPayload.add(callMap);
                }
            }

            mappedMessage.put("tool_calls", toolCallsPayload);
            mappedMessage.put("content", null); // OpenAI requires content to be null if tool_calls are present
        } else if (message.getRole() == MessageRole.TOOL) {       // This is a ToolCallResultMessage
            if (content instanceof ToolCallResultMessage) {
                ToolCallResultMessage tcResult = (ToolCallResultMessage) content;
                mappedMessage.put("tool_call_id", tcResult.toolCallId());
                mappedMessage.put("name", tcResult.name());       // Function name
                mappedMessage.put("content", tcResult.content()); // Result string (often JSON)
            } else if (content instanceof String) {               // Simplified tool result
                mappedMessage.put("content", (String) content);
                // `tool_call_id` and `name` would need to be in `message.getMeta()` or similar
                // if not using ToolCallResultMessage as content. This path is less robust.
                if (message.getMeta() != null) {
                    if (message.getMeta().containsKey("tool_call_id")) {
                        mappedMessage.put("tool_call_id", message.getMeta().get("tool_call_id").toString());
                    }
                     if (message.getMeta().containsKey("name")) { // 'name' of the tool
                        mappedMessage.put("name", message.getMeta().get("name").toString());
                    }
                }
                if (!mappedMessage.containsKey("tool_call_id")) {
                    // OpenAI requires tool_call_id for role:tool messages.
                    throw new ProviderException("Message with role TOOL must have 'tool_call_id' (and preferably 'name') if content is not ToolCallResultMessage.");
                }
            } else {
                throw new ProviderException("Content for TOOL role message must be ToolCallResultMessage or String.");
            }

        } else if (attachments != null && !attachments.isEmpty() && openAIRole.equals("user")) {
            // Multimodal content (text + images) - typically for "user" role
            List<Map<String, Object>> contentParts = new ArrayList<>();

            // Always add a text part for user messages with attachments.
            // If original content is not String or is null, use empty string for the text part.
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", (content instanceof String) ? (String) content : "");
            contentParts.add(textPart);

            for (Attachment attachment : attachments) {
                if (attachment.getType() == com.skanga.chat.enums.AttachmentType.IMAGE) {
                    contentParts.add(mapAttachmentToOpenAIFormat(attachment));
                } else {
                    // OpenAI currently only supports image attachments for multimodal messages.
                    // Other document types are ignored in terms of adding new content parts.
                    System.err.println("Warning: OpenAI mapping ignores non-image attachment type " + attachment.getType() + " for user message with images.");
                }
            }
            // If only the initial text part was added (e.g. no image attachments processed, or original content was complex)
            // and that text part is empty, and there were no images, this might not be what OpenAI expects.
            // However, if there IS an image, contentParts will have >1 element.
            // If contentParts only has one (the text part), and it was derived from a non-String or null original content,
            // it means there were no image attachments. In this case, we should not send a list.
            if (contentParts.size() == 1 && contentParts.get(0).get("type").equals("text") && !(content instanceof String)) {
                 // This case means original content was not string, and no image attachments were added.
                 // Revert to simple string content if possible, or null/empty based on original.
                 if (content == null) mappedMessage.put("content", null);
                 else mappedMessage.put("content", content.toString()); // Or try to serialize as JSON string
            } else if (contentParts.stream().anyMatch(p -> "image_url".equals(p.get("type")))) {
                 // If there's at least one image, use the contentParts list.
                mappedMessage.put("content", contentParts);
            } else if (content instanceof String) { // Only text, no images added
                mappedMessage.put("content", (String) content);
            }
            // If contentParts only has text part (from original string content) and no images were added,
            // it should simplify back to plain string content.
            else if (contentParts.size() == 1 && contentParts.get(0).get("type").equals("text")) {
                 mappedMessage.put("content", contentParts.get(0).get("text"));
            }


        } else if (content instanceof String) {
            // Simple text content
            mappedMessage.put("content", (String) content);
        } else if (content == null && openAIRole.equals("assistant")) {
            // Assistant message can have null content (e.g. if only requesting tool_calls, handled above)
             mappedMessage.put("content", null);
        } else if (content != null) {
            // Fallback for unknown content types: try to serialize to JSON string.
            // This might be useful if content is a simple Map or List.
            // However, OpenAI expects specific structures for complex types.
            try {
                mappedMessage.put("content", localObjectMapper.writeValueAsString(content));
            } catch (Exception e) {
                throw new ProviderException("Failed to serialize message content to JSON string for OpenAI: " + content.getClass().getName(), e);
            }
        }
        // If after all this, content is still null for user/system messages, OpenAI might reject.
        // Assistant messages can have null content if they include tool_calls.
        if (mappedMessage.get("content") == null && !mappedMessage.containsKey("tool_calls") && (openAIRole.equals("user") || openAIRole.equals("system"))) {
             mappedMessage.put("content", ""); // Default to empty string for user/system if no other content.
        }
        return mappedMessage;
    }

    /**
     * Maps an internal {@link MessageRole} to its string representation for the OpenAI API.
     */
    private String mapRoleToOpenAI(MessageRole role) {
        return switch (role) {
            case USER -> "user";
            case ASSISTANT, MODEL -> "assistant"; // Map our MODEL to OpenAI's "assistant"
            case SYSTEM -> "system";
            case TOOL -> "tool"; // For tool results
            case DEVELOPER -> { // OpenAI doesn't have a "developer" role. Mapping to "user"
                // Filtering out for now by returning null, or throw exception.
                System.err.println("Warning: DEVELOPER role mapped to 'user' for OpenAI");
                yield "user";
            }
            // Any other role not explicitly handled above will be filtered out by returning null.
            default -> {
                System.err.println("Warning: Unsupported message role for OpenAI: " + role + ". Message will be skipped.");
                yield null;
            }
        };
    }

    /**
     * Maps an Attachment (specifically an Image) to OpenAI's format for multimodal messages.
     */
    private Map<String, Object> mapAttachmentToOpenAIFormat(Attachment attachment) {
        if (attachment.getType() != com.skanga.chat.enums.AttachmentType.IMAGE) {
            throw new ProviderException("OpenAI currently only supports IMAGE attachments for multimodal content.");
        }

        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("type", "image_url");

        Map<String, Object> imageUrlDetails = new HashMap<>();
        if (attachment.getContentType() == AttachmentContentType.BASE64) {
            imageUrlDetails.put("url", "data:" + attachment.getMediaType() + ";base64," + attachment.getContent());
        } else if (attachment.getContentType() == AttachmentContentType.URL) {
            imageUrlDetails.put("url", attachment.getContent());
        } else {
            throw new ProviderException("Unsupported content type for OpenAI image attachment: " + attachment.getContentType());
        }
        // OpenAI allows an optional "detail" field: "auto", "low", "high". Defaulting to "auto".
        imageUrlDetails.put("detail", "auto");

        imagePart.put("image_url", imageUrlDetails);
        return imagePart;
    }
}

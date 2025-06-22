
// anthropic/AnthropicMessageMapper.java
package com.skanga.providers.anthropic;

import com.fasterxml.jackson.core.type.TypeReference;
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

public class AnthropicMessageMapper implements MessageMapper {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Map<String, Object>> map(List<Message> messages) {
        if (messages == null) {
            return new ArrayList<>();
        }
        // Anthropic requires alternating user/assistant messages for actual conversation turns.
        // System prompts are handled at the top level of the request, not in the messages array.
        // This mapper will filter out system messages; the provider should handle placing the system prompt.
        return messages.stream()
            .filter(msg -> msg.getRole() != MessageRole.SYSTEM)
            .map(this::mapMessageInternal)
            .collect(Collectors.toList());
    }

    private Map<String, Object> mapMessageInternal(Message message) {
        String role = mapRoleToAnthropic(message.getRole());
        if (role == null) {
            // This case should ideally not be reached if map() filters SYSTEM messages.
            // Roles like DEVELOPER would also be filtered or throw here.
            throw new ProviderException("Cannot map role '" + message.getRole() + "' to an Anthropic role for message content.");
        }

        Map<String, Object> mappedMessage = new HashMap<>();
        mappedMessage.put("role", role);

        List<Map<String, Object>> contentBlocks = new ArrayList<>();
        Object msgContent = message.getContent();

        // Case 1: Content is a string (text message)
        if (msgContent instanceof String) {
            String textContent = (String) msgContent;
            if (!textContent.isEmpty() || message.getAttachments().isEmpty()) {
                contentBlocks.add(Map.of("type", "text", "text", textContent));
            }
        }
        // Case 2: Content is a ToolCallMessage (AI requests to use tools)
        else if (role.equals("assistant") && msgContent instanceof ToolCallMessage) {
            ToolCallMessage tcMessage = (ToolCallMessage) msgContent;
            for (ToolCallMessage.ToolCall tc : tcMessage.toolCalls()) {
                Map<String, Object> toolUseBlock = new HashMap<>();
                toolUseBlock.put("type", "tool_use");
                toolUseBlock.put("id", tc.id());
                toolUseBlock.put("name", tc.function().name());

                try {
                    Object parsedInput = objectMapper.readValue(tc.function().arguments(), Map.class);
                    toolUseBlock.put("input", parsedInput);
                } catch (Exception e) {
                    throw new ProviderException("Failed to parse tool arguments to JSON object for Anthropic: " + tc.function().arguments(), e);
                }
                contentBlocks.add(toolUseBlock);
            }
        }
        // Case 3: Content is a ToolCallResultMessage (user provides tool results)
        // These are mapped to role "user" with specific content blocks.
        else if (role.equals("user") && msgContent instanceof ToolCallResultMessage) {
            ToolCallResultMessage trMessage = (ToolCallResultMessage) msgContent;
            Map<String, Object> toolResultBlock = new HashMap<>();
            toolResultBlock.put("type", "tool_result");
            toolResultBlock.put("tool_use_id", trMessage.toolCallId());
            // Anthropic's 'content' for tool_result can be a string or JSON object.
            // We'll pass the string content directly. For JSON object content,
            // the caller should ensure trMessage.content() is a JSON string that
            // Anthropic can interpret as an object if needed, or we could parse it here.
            toolResultBlock.put("content", trMessage.content());
            // Optionally add "is_error": true if the tool execution failed.
            contentBlocks.add(toolResultBlock);
        }
        // Case 4: Unhandled content type, try toString() as a fallback if it's not empty
        else if (msgContent != null && !msgContent.toString().isEmpty()) {
             contentBlocks.add(Map.of("type", "text", "text", msgContent.toString()));
        }


        // Handle attachments (primarily images for Anthropic)
        if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
            for (Attachment attachment : message.getAttachments()) {
                if (attachment.getType() == com.skanga.chat.enums.AttachmentType.IMAGE) {
                    Map<String, Object> imageBlock = new HashMap<>();
                    imageBlock.put("type", "image");
                    Map<String, Object> sourceBlock = new HashMap<>();
                    if (attachment.getContentType() == AttachmentContentType.BASE64) {
                        sourceBlock.put("type", "base64");
                        sourceBlock.put("media_type", attachment.getMediaType());
                        sourceBlock.put("data", attachment.getContent());
                    } else {
                         throw new ProviderException("Anthropic image attachments must be base64 encoded. Type '" + attachment.getContentType() + "' not supported.");
                    }
                    imageBlock.put("source", sourceBlock);
                    contentBlocks.add(imageBlock);
                }
                // Other attachment types could be handled here if supported by Anthropic.
            }
        }

        mappedMessage.put("content", contentBlocks);
        return mappedMessage;
    }

    private String mapRoleToAnthropic(MessageRole role) {
        return switch (role) {
            case USER -> "user";
            case ASSISTANT, MODEL -> "assistant"; // MODEL role from our system is mapped to 'assistant'
            case TOOL -> "user";                  // Tool results are provided by the "user" in Anthropic's model
            case SYSTEM -> null;                  // System prompts are handled at the top level for Anthropic
                                                  // Filtered out by the calling map() method
            default ->
                // Roles like DEVELOPER are not directly supported by Anthropic's message roles.
                    throw new ProviderException("Unsupported message role for Anthropic: " + role);
        };
    }
}

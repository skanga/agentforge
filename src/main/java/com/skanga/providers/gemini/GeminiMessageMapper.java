
// gemini/GeminiMessageMapper.java
package com.skanga.providers.gemini;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.chat.attachments.Attachment;
import com.skanga.chat.enums.AttachmentContentType;
import com.skanga.chat.enums.MessageRole;
import com.skanga.chat.messages.Message;
import com.skanga.core.exceptions.ProviderException;
import com.skanga.core.messages.ToolCallMessage;
import com.skanga.core.messages.ToolCallResultMessage;
import com.skanga.providers.gemini.dto.*;
import com.skanga.providers.mappers.MessageMapper;

import java.util.*;
import java.util.stream.Collectors;

public class GeminiMessageMapper implements MessageMapper {
    private final ObjectMapper localObjectMapper = new ObjectMapper();

    @Override
    public List<Map<String, Object>> map(List<Message> messages) {
        if (messages == null) {
            return new ArrayList<>();
        }
        // Gemini expects "user" and "model" roles. System messages are handled separately.
        // ToolCallResultMessage (role TOOL from our side) maps to a "user" role message with a functionResponse part.
        // ToolCallMessage (role ASSISTANT from our side) maps to a "model" role message with a functionCall part.
        return messages.stream()
            .filter(msg -> msg.getRole() != MessageRole.SYSTEM)
            .map(this::mapMessageToGeminiContent)
            .collect(Collectors.toList());
    }

    // This method will now return Map<String, Object> to directly match Gemini's Content structure
    private Map<String, Object> mapMessageToGeminiContent(Message message) {
        String role = mapRoleToGemini(message.getRole());
        Map<String, Object> geminiContent = new HashMap<>();
        geminiContent.put("role", role);

        List<GeminiPart> parts = new ArrayList<>();
        Object msgContent = message.getContent();

        // Part 1: Handle primary content (text, tool calls/responses)
        if (msgContent instanceof String) {
            String textContent = (String) msgContent;
            if (!textContent.isEmpty()) {
                parts.add(new GeminiPart(textContent));
            }
        } else if (role.equals("model") && msgContent instanceof ToolCallMessage) {
            // Assistant requests tool call
            ToolCallMessage tcMessage = (ToolCallMessage) msgContent;
            for (ToolCallMessage.ToolCall tc : tcMessage.toolCalls()) {
                try {
                    Map<String, Object> argsMap = localObjectMapper.readValue(
                        tc.function().arguments(), new TypeReference<>() {}
                    );
                    parts.add(new GeminiPart(new GeminiFunctionCall(tc.function().name(), argsMap)));
                } catch (Exception e) {
                    throw new ProviderException("Failed to parse tool arguments for Gemini: " + tc.function().arguments(), e);
                }
            }
        } else if (role.equals("user") && msgContent instanceof ToolCallResultMessage) {
            // User provides tool result
            ToolCallResultMessage trMessage = (ToolCallResultMessage) msgContent;
            // Gemini's functionResponse wants the response part to be an object.
            // If trMessage.content() is a simple string, we might need to wrap it,
            // or assume it's already a JSON string representing an object.
            // For now, let's assume trMessage.content() is a JSON string of the result object.
            try {
                Map<String, Object> responseData = localObjectMapper.readValue(
                    trMessage.content(), new TypeReference<>() {}
                );
                parts.add(new GeminiPart(new GeminiFunctionResponse(trMessage.name(), responseData)));
            } catch (Exception e) {
                 // If content is not a valid JSON object string, pass it as a simple string content part,
                 // but this might not be what Gemini expects for functionResponse.
                 // This path is problematic for Gemini's structured functionResponse.
                 System.err.println("Warning: ToolCallResultMessage content for Gemini was not a valid JSON object string. Content: " + trMessage.content() + ". Error: " + e.getMessage());
                 // Fallback: create a text part, but this is not a valid functionResponse for Gemini.
                 // A better approach is to ensure trMessage.content() is always a JSON string for Gemini.
                 // parts.add(new GeminiPart("Error processing tool result: " + trMessage.content()));
                 throw new ProviderException("ToolCallResultMessage content for Gemini must be a JSON object string. Received: " + trMessage.content(), e);
            }
        } else if (msgContent != null) { // Fallback for unknown content types
            parts.add(new GeminiPart(msgContent.toString()));
        }

        // Part 2: Handle attachments (images)
        if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
            for (Attachment attachment : message.getAttachments()) {
                if (attachment.getType() == com.skanga.chat.enums.AttachmentType.IMAGE) {
                    if (attachment.getContentType() == AttachmentContentType.BASE64) {
                        parts.add(new GeminiPart(new GeminiPart.InlineData(attachment.getMediaType(), attachment.getContent())));
                    } else if (attachment.getContentType() == AttachmentContentType.URL) {
                        // This part is speculative as Gemini might prefer inlineData or specific URL handling.
                        // For now, assuming fileData for URLs.
                        // parts.add(new GeminiPart(new GeminiPart.FileData(attachment.getMediaType(), attachment.getContent())));
                        System.err.println("Warning: URL image attachments for Gemini are mapped to text for now. Inline base64 is preferred.");
                        parts.add(new GeminiPart("Image URL: " + attachment.getContent())); // Fallback
                    }
                }
            }
        }

        // Ensure at least one part
        if (parts.isEmpty() && (msgContent instanceof String && ((String) msgContent).isEmpty())) {
            // Handle genuinely empty messages if Gemini requires at least one part (e.g. empty text)
            parts.add(new GeminiPart(""));
        }


        geminiContent.put("parts", parts.stream()
            .map(part -> localObjectMapper.convertValue(part, new TypeReference<Map<String, Object>>() {}))
            .collect(Collectors.toList()));

        return geminiContent;
    }

    private String mapRoleToGemini(MessageRole role) {
        return switch (role) {
            case USER -> "user";
            case MODEL, ASSISTANT -> "model";
            case TOOL -> "user"; // Tool results submitted by user role
            case SYSTEM -> null; // Filtered out before this method
            default -> throw new ProviderException("Unsupported message role for Gemini: " + role);
        };
    }
}

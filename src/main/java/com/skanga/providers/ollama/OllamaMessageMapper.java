
// ollama/OllamaMessageMapper.java
package com.skanga.providers.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.chat.enums.AttachmentContentType;
import com.skanga.chat.enums.MessageRole;
import com.skanga.chat.attachments.Attachment;
import com.skanga.chat.messages.Message;
import com.skanga.core.messages.ToolCallMessage;
import com.skanga.core.messages.ToolCallResultMessage;
import com.skanga.core.exceptions.ProviderException;
import com.skanga.providers.mappers.MessageMapper;

import java.util.*;
import java.util.stream.Collectors;

public class OllamaMessageMapper implements MessageMapper {
    private final ObjectMapper localObjectMapper = new ObjectMapper();

    @Override
    public List<Map<String, Object>> map(List<Message> messages) {
        if (messages == null) {
            return new ArrayList<>();
        }
        // Ollama handles system messages as a top-level "system" parameter.
        // The mapper should filter these out from the main messages list.
        return messages.stream()
                .filter(msg -> msg.getRole() != MessageRole.SYSTEM)
                .map(this::mapMessageToOllamaFormat)
                .collect(Collectors.toList());
    }

    private Map<String, Object> mapMessageToOllamaFormat(Message message) {
        Map<String, Object> ollamaMessage = new HashMap<>();
        ollamaMessage.put("role", mapRoleToOllama(message.getRole()));

        List<String> base64Images = new ArrayList<>();
        Object content = message.getContent();

        // Handle attachments (images must be base64)
        if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
            for (Attachment attachment : message.getAttachments()) {
                if (attachment.getType() == com.skanga.chat.enums.AttachmentType.IMAGE) {
                    if (attachment.getContentType() == AttachmentContentType.BASE64) {
                        base64Images.add(attachment.getContent());
                    } else {
                        throw new ProviderException("Ollama image attachments must be base64 encoded. URL content type not supported.");
                    }
                } else {
                    throw new ProviderException("Ollama only supports image attachments. Document attachments are not supported.");
                }
            }
        }
        if (!base64Images.isEmpty()) {
            ollamaMessage.put("images", base64Images);
        }

        // Handle content: text, tool calls, tool results
        if (content instanceof String) {
            ollamaMessage.put("content", content);
        } else if (content instanceof ToolCallMessage tcMessage) { // Assistant requests tool calls
            // Ollama expects tool_calls in a structure similar to OpenAI
            List<Map<String, Object>> toolCallsPayload = new ArrayList<>();
            for (ToolCallMessage.ToolCall tc : tcMessage.toolCalls()) {
                Map<String, Object> callMap = new HashMap<>();
                callMap.put("type", "function"); // Assuming "function" type
                Map<String, Object> functionMap = new HashMap<>();
                functionMap.put("name", tc.function().name());
                try {
                    // Ollama expects arguments as a JSON object (map)
                    Map<String, Object> argumentsMap = localObjectMapper.readValue(tc.function().arguments(), Map.class);
                    functionMap.put("arguments", argumentsMap);
                } catch (Exception e) {
                    throw new ProviderException("Failed to parse tool arguments string to JSON object for Ollama: " + tc.function().arguments(), e);
                }
                callMap.put("function", functionMap);
                toolCallsPayload.add(callMap);
            }
            ollamaMessage.put("tool_calls", toolCallsPayload);
            // If there's text content alongside tool calls, Ollama might support it in the `content` field.
            // For now, if ToolCallMessage is the content, we prioritize that.
            // If the original message had text AND tool calls, the text might be lost here unless handled.
            // Current Message class has Object content, so it's one or the other.
            if (ollamaMessage.get("content") == null) ollamaMessage.put("content",""); // Ollama might need content field even with tool_calls

        } else if (message.getRole() == MessageRole.TOOL && content instanceof ToolCallResultMessage) { // User provides tool result
            ToolCallResultMessage trMessage = (ToolCallResultMessage) content;
            ollamaMessage.put("content", trMessage.content());

            /*
            // Map to Ollama's expected format for tool results (role: "tool")
            // This is directly handled by mapRoleToOllama setting role to "tool"
            // The content of the message for Ollama should be the string result.
            // The tool_call_id and name are part of a tool_calls array containing a single call object.
            Map<String, Object> toolCallResultPayload = new HashMap<>();
            toolCallResultPayload.put("id", trMessage.toolCallId()); // Not standard in Ollama request for "tool" role, but we include it for structure
            toolCallResultPayload.put("type", "function"); // Assuming it was a function call

            Map<String, Object> functionResultMap = new HashMap<>();
            functionResultMap.put("name", trMessage.name());
            // Content for the result should be a string for Ollama's "tool" role message content.
            // If trMessage.content() is structured JSON, it needs to be passed as a string.
            functionResultMap.put("arguments", Map.of("result", trMessage.content())); // Wrapping result for clarity

            // This structure is more aligned with how an LLM might *request* a call.
            // For submitting results, Ollama expects:
            // { "role": "tool", "content": "<result_string>", "tool_call_id": "<id>" } (if using newer OpenAI style)
            // Or just content if the model infers which tool call it's for.
            // Let's simplify: the content itself is the result string for the "tool" role message.
            // The `tool_calls` field is for when the *assistant* makes calls.
            ollamaMessage.put("content", trMessage.content());
            // Ollama doesn't explicitly take `tool_call_id` at the message level for `role: tool` in the same way OpenAI does.
            // It's often part of the `tool_calls` array if the model is designed to use it for responses.
            // For submitting results, the content string is key. If the model needs to correlate,
            // it might be via context or if Ollama adds more explicit support.
            // For now, we'll add a non-standard `tool_call_id` field to the message if needed by the model.
            // This part of mapping might need adjustment based on specific Ollama model behavior with tools.
            // Let's assume for now the content string is sufficient and the model handles context.
            // If a specific model expects tool_call_id with role:tool, it might be passed in content or options.
            */
        } else if (content != null) {
            ollamaMessage.put("content", content.toString());
        } else {
             // If content is null but there are images, content can be an empty string.
            if (!base64Images.isEmpty()) {
                ollamaMessage.put("content", "");
            } else {
                // If no content and no images, it's an empty message.
                // Depending on Ollama's API, this might be an issue or okay.
                // To be safe, ensure content key exists, even if empty.
                ollamaMessage.put("content", "");
            }
        }

        return ollamaMessage;
    }

    private String mapRoleToOllama(MessageRole role) {
        switch (role) {
            case USER:
                return "user";
            case ASSISTANT:
            case MODEL:
                return "assistant";
            case SYSTEM: // Filtered out by map(), should not reach here.
                return "system"; // Ollama supports top-level system prompt or system role message
            case TOOL: // This is for sending results back to the model.
                return "tool";
            default:
                throw new ProviderException("Unsupported message role for Ollama: " + role);
        }
    }
}

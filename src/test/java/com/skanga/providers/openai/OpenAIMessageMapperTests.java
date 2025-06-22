
// 12. MessageMapperTests.java
package com.skanga.providers.openai;

import com.skanga.chat.attachments.Attachment;
import com.skanga.chat.attachments.Image;
import com.skanga.chat.enums.AttachmentContentType;
import com.skanga.chat.enums.AttachmentType;
import com.skanga.chat.enums.MessageRole;
import com.skanga.chat.messages.Message;
import com.skanga.core.exceptions.ProviderException;
import com.skanga.core.messages.ToolCallMessage;
import com.skanga.core.messages.ToolCallResultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAIMessageMapperTests {

    private OpenAIMessageMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OpenAIMessageMapper();
    }

    @Test
    void map_WithSimpleUserMessage_ShouldMapCorrectly() {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Hello, how are you?")
        );

        // Act
        List<Map<String, Object>> result = mapper.map(messages);

        // Assert
        assertThat(result).hasSize(1);
        Map<String, Object> mappedMessage = result.get(0);
        assertThat(mappedMessage.get("role")).isEqualTo("user");
        assertThat(mappedMessage.get("content")).isEqualTo("Hello, how are you?");
    }

    @Test
    void map_WithSystemMessage_ShouldMapCorrectly() {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.SYSTEM, "You are a helpful assistant.")
        );

        // Act
        List<Map<String, Object>> result = mapper.map(messages);

        // Assert
        assertThat(result).hasSize(1);
        Map<String, Object> mappedMessage = result.get(0);
        assertThat(mappedMessage.get("role")).isEqualTo("system");
        assertThat(mappedMessage.get("content")).isEqualTo("You are a helpful assistant.");
    }

    @Test
    void map_WithAssistantMessage_ShouldMapCorrectly() {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.ASSISTANT, "I'm doing well, thank you!")
        );

        // Act
        List<Map<String, Object>> result = mapper.map(messages);

        // Assert
        assertThat(result).hasSize(1);
        Map<String, Object> mappedMessage = result.get(0);
        assertThat(mappedMessage.get("role")).isEqualTo("assistant");
        assertThat(mappedMessage.get("content")).isEqualTo("I'm doing well, thank you!");
    }

    @Test
    void map_WithToolCallMessage_ShouldMapToolCalls() {
        // Arrange
        ToolCallMessage.ToolCall toolCall = new ToolCallMessage.ToolCall(
                "call-123", "function",
                new ToolCallMessage.FunctionCall("get_weather", "{\"location\":\"NYC\"}")
        );
        ToolCallMessage toolCallMessage = new ToolCallMessage("msg-123", Arrays.asList(toolCall));
        Message message = new Message(MessageRole.ASSISTANT, toolCallMessage);

        // Act
        List<Map<String, Object>> result = mapper.map(Arrays.asList(message));

        // Assert
        assertThat(result).hasSize(1);
        Map<String, Object> mappedMessage = result.get(0);
        assertThat(mappedMessage.get("role")).isEqualTo("assistant");

        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) mappedMessage.get("tool_calls");
        assertThat(toolCalls).hasSize(1);

        Map<String, Object> mappedToolCall = toolCalls.get(0);
        assertThat(mappedToolCall.get("id")).isEqualTo("call-123");
        assertThat(mappedToolCall.get("type")).isEqualTo("function");

        Map<String, Object> function = (Map<String, Object>) mappedToolCall.get("function");
        assertThat(function.get("name")).isEqualTo("get_weather");
        assertThat(function.get("arguments")).isEqualTo("{\"location\":\"NYC\"}");
    }

    @Test
    void map_WithToolResultMessage_ShouldMapToolResult() {
        // Arrange
        ToolCallResultMessage toolResult = new ToolCallResultMessage(
                "call-123", "tool", "get_weather", "Sunny, 75°F"
        );
        Message message = new Message(MessageRole.TOOL, toolResult);

        // Act
        List<Map<String, Object>> result = mapper.map(Arrays.asList(message));

        // Assert
        assertThat(result).hasSize(1);
        Map<String, Object> mappedMessage = result.get(0);
        assertThat(mappedMessage.get("role")).isEqualTo("tool");
        assertThat(mappedMessage.get("tool_call_id")).isEqualTo("call-123");
        assertThat(mappedMessage.get("name")).isEqualTo("get_weather");
        assertThat(mappedMessage.get("content")).isEqualTo("Sunny, 75°F");
    }

    @Test
    void map_WithImageAttachment_ShouldMapMultimodalContent() {
        // Arrange
        Image image = new Image("base64data", AttachmentContentType.BASE64, "image/jpeg");
        Message message = new Message(MessageRole.USER, "What's in this image?");
        message.addAttachment(image);

        // Act
        List<Map<String, Object>> result = mapper.map(Arrays.asList(message));

        // Assert
        assertThat(result).hasSize(1);
        Map<String, Object> mappedMessage = result.get(0);
        assertThat(mappedMessage.get("role")).isEqualTo("user");

        List<Map<String, Object>> contentParts = (List<Map<String, Object>>) mappedMessage.get("content");
        assertThat(contentParts).hasSize(2); // Text + Image

        // Check text part
        Map<String, Object> textPart = contentParts.get(0);
        assertThat(textPart.get("type")).isEqualTo("text");
        assertThat(textPart.get("text")).isEqualTo("What's in this image?");

        // Check image part
        Map<String, Object> imagePart = contentParts.get(1);
        assertThat(imagePart.get("type")).isEqualTo("image_url");

        Map<String, Object> imageUrl = (Map<String, Object>) imagePart.get("image_url");
        assertThat(imageUrl.get("url")).isEqualTo("data:image/jpeg;base64,base64data");
    }

    @Test
    void map_WithUrlImageAttachment_ShouldMapImageUrl() {
        // Arrange
        Image image = new Image("https://example.com/image.jpg", AttachmentContentType.URL, "image/jpeg");
        Message message = new Message(MessageRole.USER, "Analyze this image");
        message.addAttachment(image);

        // Act
        List<Map<String, Object>> result = mapper.map(Arrays.asList(message));

        // Assert
        Map<String, Object> mappedMessage = result.get(0);
        List<Map<String, Object>> contentParts = (List<Map<String, Object>>) mappedMessage.get("content");
        Map<String, Object> imagePart = contentParts.get(1);
        Map<String, Object> imageUrl = (Map<String, Object>) imagePart.get("image_url");

        assertThat(imageUrl.get("url")).isEqualTo("https://example.com/image.jpg");
    }

    @Test
    void map_WithUnsupportedAttachmentType_ShouldHandleGracefully() {
        // Arrange
        Attachment document = new Attachment(AttachmentType.DOCUMENT, "content", AttachmentContentType.BASE64, "application/pdf");
        Message message = new Message(MessageRole.USER, "Process this document");
        message.addAttachment(document);

        // Act
        List<Map<String, Object>> result = mapper.map(Arrays.asList(message));

        // Assert
        assertThat(result).hasSize(1);
        Map<String, Object> mappedMessage = result.get(0);
        assertThat(mappedMessage.get("role")).isEqualTo("user");
        assertThat(mappedMessage.get("content")).isEqualTo("Process this document");
        // Document attachment should be ignored for OpenAI
    }

    @Test
    void map_WithEmptyContent_ShouldHandleGracefully() {
        // Arrange
        Message message = new Message(MessageRole.USER, "");

        // Act
        List<Map<String, Object>> result = mapper.map(Arrays.asList(message));

        // Assert
        assertThat(result).hasSize(1);
        Map<String, Object> mappedMessage = result.get(0);
        assertThat(mappedMessage.get("content")).isEqualTo("");
    }

    @Test
    void map_WithNullContent_ShouldHandleGracefully() {
        // Arrange
        Message message = new Message(MessageRole.ASSISTANT, null);

        // Act
        List<Map<String, Object>> result = mapper.map(Arrays.asList(message));

        // Assert
        assertThat(result).hasSize(1);
        Map<String, Object> mappedMessage = result.get(0);
        assertThat(mappedMessage.get("content")).isNull();
    }

    @Test
    void map_WithUnsupportedRole_ShouldThrowException() {
        // Arrange
        Message message = new Message(MessageRole.MODEL, "Test content");

        // Act & Assert
        assertThrows(ProviderException.class, () ->
                mapper.map(Arrays.asList(message)));
    }

    @Test
    void map_WithToolRoleButStringContent_ShouldRequireMetadata() {
        // Arrange
        Message message = new Message(MessageRole.TOOL, "Tool result");
        // Missing required metadata

        // Act & Assert
        ProviderException exception = assertThrows(ProviderException.class, () ->
                mapper.map(Arrays.asList(message)));
        assertThat(exception.getMessage()).contains("tool_call_id");
    }

    @Test
    void map_WithToolRoleAndMetadata_ShouldMapCorrectly() {
        // Arrange
        Message message = new Message(MessageRole.TOOL, "Tool executed successfully");
        message.addMetadata("tool_call_id", "call-456");
        message.addMetadata("name", "test_tool");

        // Act
        List<Map<String, Object>> result = mapper.map(Arrays.asList(message));

        // Assert
        assertThat(result).hasSize(1);
        Map<String, Object> mappedMessage = result.get(0);
        assertThat(mappedMessage.get("role")).isEqualTo("tool");
        assertThat(mappedMessage.get("tool_call_id")).isEqualTo("call-456");
        assertThat(mappedMessage.get("name")).isEqualTo("test_tool");
        assertThat(mappedMessage.get("content")).isEqualTo("Tool executed successfully");
    }

    @Test
    void map_WithMultipleMessages_ShouldMapAll() {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.SYSTEM, "You are helpful"),
                new Message(MessageRole.USER, "Hello"),
                new Message(MessageRole.ASSISTANT, "Hi there!")
        );

        // Act
        List<Map<String, Object>> result = mapper.map(messages);

        // Assert
        assertThat(result).hasSize(3);
        assertThat(result.get(0).get("role")).isEqualTo("system");
        assertThat(result.get(1).get("role")).isEqualTo("user");
        assertThat(result.get(2).get("role")).isEqualTo("assistant");
    }

    @Test
    void map_WithComplexObject_ShouldSerializeToJson() {
        // Arrange
        Map<String, Object> complexContent = Map.of(
                "type", "analysis",
                "data", Arrays.asList(1, 2, 3),
                "metadata", Map.of("source", "test")
        );
        Message message = new Message(MessageRole.ASSISTANT, complexContent);

        // Act
        List<Map<String, Object>> result = mapper.map(Arrays.asList(message));

        // Assert
        assertThat(result).hasSize(1);
        Map<String, Object> mappedMessage = result.get(0);
        String content = (String) mappedMessage.get("content");
        assertThat(content).contains("analysis");
        assertThat(content).contains("test");
    }

    @Test
    void map_WithDeveloperRole_ShouldMapToUser() {
        // Arrange
        Message message = new Message(MessageRole.DEVELOPER, "Debug this code");

        // Act
        List<Map<String, Object>> result = mapper.map(Arrays.asList(message));

        // Assert
        assertThat(result).hasSize(1);
        Map<String, Object> mappedMessage = result.get(0);
        assertThat(mappedMessage.get("role")).isEqualTo("user");
        assertThat(mappedMessage.get("content")).isEqualTo("Debug this code");
    }

    @Test
    void map_WithUnsupportedImageContentType_ShouldThrowException() {
        // Arrange
        Attachment unsupportedImage = new Attachment(
                AttachmentType.IMAGE, "content",
                AttachmentContentType.valueOf("UNSUPPORTED"), "image/jpeg"
        );
        Message message = new Message(MessageRole.USER, "Check this image");
        message.addAttachment(unsupportedImage);

        // Act & Assert
        assertThrows(ProviderException.class, () ->
                mapper.map(Arrays.asList(message)));
    }

    @Test
    void map_WithEmptyMessages_ShouldReturnEmptyList() {
        // Act
        List<Map<String, Object>> result = mapper.map(Collections.emptyList());

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void map_WithNullMessageList_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                mapper.map(null));
    }

    @Test
    void map_WithAssistantToolCallAndEmptyContent_ShouldSetContentToNull() {
        // Arrange
        ToolCallMessage.ToolCall toolCall = new ToolCallMessage.ToolCall(
                "call-789", "function",
                new ToolCallMessage.FunctionCall("calculate", "{\"x\":5,\"y\":10}")
        );
        ToolCallMessage toolCallMessage = new ToolCallMessage("msg-789", Arrays.asList(toolCall));
        Message message = new Message(MessageRole.ASSISTANT, toolCallMessage);
        // Simulate empty string content alongside tool calls
        message.setContent("");

        // Act
        List<Map<String, Object>> result = mapper.map(Arrays.asList(message));

        // Assert
        assertThat(result).hasSize(1);
        Map<String, Object> mappedMessage = result.get(0);
        assertThat(mappedMessage.get("content")).isNull();
        assertThat(mappedMessage).containsKey("tool_calls");
    }

    @Test
    void map_WithImageOnlyMessage_ShouldCreateMultimodalContent() {
        // Arrange
        Image image = new Image("image_data_here", AttachmentContentType.BASE64, "image/png");
        Message message = new Message(MessageRole.USER, ""); // Empty text content
        message.addAttachment(image);

        // Act
        List<Map<String, Object>> result = mapper.map(Arrays.asList(message));

        // Assert
        assertThat(result).hasSize(1);
        Map<String, Object> mappedMessage = result.get(0);

        List<Map<String, Object>> contentParts = (List<Map<String, Object>>) mappedMessage.get("content");
        assertThat(contentParts).hasSize(2); // Empty text + image

        // Verify empty text part is still included
        Map<String, Object> textPart = contentParts.get(0);
        assertThat(textPart.get("type")).isEqualTo("text");
        assertThat(textPart.get("text")).isEqualTo("");

        // Verify image part
        Map<String, Object> imagePart = contentParts.get(1);
        assertThat(imagePart.get("type")).isEqualTo("image_url");
    }

    @Test
    void map_WithMultipleImages_ShouldHandleAll() {
        // Arrange
        Image image1 = new Image("data1", AttachmentContentType.BASE64, "image/jpeg");
        Image image2 = new Image("https://example.com/image2.png", AttachmentContentType.URL, "image/png");

        Message message = new Message(MessageRole.USER, "Compare these images");
        message.addAttachment(image1);
        message.addAttachment(image2);

        // Act
        List<Map<String, Object>> result = mapper.map(Arrays.asList(message));

        // Assert
        assertThat(result).hasSize(1);
        Map<String, Object> mappedMessage = result.get(0);

        List<Map<String, Object>> contentParts = (List<Map<String, Object>>) mappedMessage.get("content");
        assertThat(contentParts).hasSize(3); // text + 2 images

        // Verify both image parts are present
        long imageCount = contentParts.stream()
                .filter(part -> "image_url".equals(part.get("type")))
                .count();
        assertThat(imageCount).isEqualTo(2);
    }

    @Test
    void map_WithToolCallInvalidJson_ShouldHandleGracefully() {
        // Arrange
        ToolCallMessage.ToolCall toolCall = new ToolCallMessage.ToolCall(
                "call-invalid", "function",
                new ToolCallMessage.FunctionCall("test_func", "invalid json {}")
        );
        ToolCallMessage toolCallMessage = new ToolCallMessage("msg-invalid", Arrays.asList(toolCall));
        Message message = new Message(MessageRole.ASSISTANT, toolCallMessage);

        // Act
        List<Map<String, Object>> result = mapper.map(Arrays.asList(message));

        // Assert
        assertThat(result).hasSize(1);
        Map<String, Object> mappedMessage = result.get(0);

        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) mappedMessage.get("tool_calls");
        assertThat(toolCalls).hasSize(1);

        Map<String, Object> function = (Map<String, Object>) toolCalls.get(0).get("function");
        // Should preserve the original invalid JSON string
        assertThat(function.get("arguments")).isEqualTo("invalid json {}");
    }

    @Test
    void map_WithSkippedUnsupportedRole_ShouldFilterOut() {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Valid message"),
                new Message(MessageRole.valueOf("UNSUPPORTED_ROLE"), "Should be filtered"),
                new Message(MessageRole.ASSISTANT, "Another valid message")
        );

        // Act
        List<Map<String, Object>> result = mapper.map(messages);

        // Assert
        // Should only return the mappable messages, filtering out unsupported ones
        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("content")).isEqualTo("Valid message");
        assertThat(result.get(1).get("content")).isEqualTo("Another valid message");
    }

    @Test
    void map_WithNonImageAttachmentType_ShouldIgnoreAttachment() {
        // Arrange
        Attachment documentAttachment = new Attachment(
                AttachmentType.DOCUMENT, "pdf_content",
                AttachmentContentType.BASE64, "application/pdf"
        );
        Message message = new Message(MessageRole.USER, "Process this document");
        message.addAttachment(documentAttachment);

        // Act
        List<Map<String, Object>> result = mapper.map(Arrays.asList(message));

        // Assert
        assertThat(result).hasSize(1);
        Map<String, Object> mappedMessage = result.get(0);
        // Should fall back to simple string content, ignoring the document attachment
        assertThat(mappedMessage.get("content")).isEqualTo("Process this document");
        assertThat(mappedMessage).doesNotContainKey("attachments");
    }
}
package com.skanga.chat.history;

import com.skanga.chat.attachments.Attachment;
import com.skanga.chat.attachments.Image;
import com.skanga.chat.enums.AttachmentContentType;
import com.skanga.chat.enums.AttachmentType;
import com.skanga.chat.enums.MessageRole;
import com.skanga.chat.messages.AssistantMessage;
import com.skanga.chat.messages.Message;
import com.skanga.chat.messages.UserMessage;
import com.skanga.core.messages.ToolCallMessage;
import com.skanga.core.messages.ToolCallResultMessage;
import com.fasterxml.jackson.databind.ObjectMapper; // For creating complex content maps

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AbstractChatHistoryTests {

    // Concrete subclass for testing abstract methods
    static class TestableAbstractChatHistory extends AbstractChatHistory {
        List<Message> persistedMessages = new ArrayList<>();
        boolean cleared = false;

        protected TestableAbstractChatHistory(int contextWindow) {
            super(contextWindow);
        }

        @Override
        protected void storeMessage(Message message) {
            // Simulate storage: find if message with same ID exists, replace, else add.
            // For simplicity here, just add. A real store might update.
            persistedMessages.removeIf(m -> m.getRole() == message.getRole() && m.getContent().equals(message.getContent())); // Simplistic removal
            persistedMessages.add(message);
        }

        @Override
        protected void clear() {
            persistedMessages.clear();
            cleared = true;
        }

        // Make deserializeMessages public for testing
        @Override
        public List<Message> deserializeMessages(List<Map<String, Object>> messagesData) {
            return super.deserializeMessages(messagesData);
        }
    }

    private TestableAbstractChatHistory chatHistory;
    private final ObjectMapper objectMapper = new ObjectMapper();


    @BeforeEach
    void setUp() {
        chatHistory = new TestableAbstractChatHistory(10); // Context window of 10
    }

    @Test
    void deserializeMessages_handlesVariousMessageTypesAndContent() throws Exception {
        List<Map<String, Object>> messagesData = new ArrayList<>();

        // 1. UserMessage
        Map<String, Object> userMsgData = new HashMap<>();
        userMsgData.put("role", "USER");
        userMsgData.put("content", "Hello there!");
        userMsgData.put("message_class_type", "UserMessage");
        messagesData.add(userMsgData);

        // 2. AssistantMessage with simple text
        Map<String, Object> assistantMsgData = new HashMap<>();
        assistantMsgData.put("role", "ASSISTANT");
        assistantMsgData.put("content", "Hi! How can I help?");
        assistantMsgData.put("message_class_type", "AssistantMessage");
        assistantMsgData.put("usage", Map.of("prompt_tokens", 10, "completion_tokens", 5, "total_tokens", 15));
        messagesData.add(assistantMsgData);

        // 3. AssistantMessage with ToolCallMessage content
        ToolCallMessage.FunctionCall funcCall = new ToolCallMessage.FunctionCall("get_weather", "{\"location\":\"LA\"}");
        ToolCallMessage.ToolCall toolCall = new ToolCallMessage.ToolCall("tc1", "function", funcCall);
        ToolCallMessage tcContent = new ToolCallMessage("tcm1", List.of(toolCall));

        Map<String, Object> assistantToolCallMsgData = new HashMap<>();
        assistantToolCallMsgData.put("role", "ASSISTANT");
        assistantToolCallMsgData.put("content", objectMapper.convertValue(tcContent, Map.class)); // Content as Map
        assistantToolCallMsgData.put("content_type", "ToolCallMessage"); // Hint for deserializer
        assistantToolCallMsgData.put("message_class_type", "AssistantMessage");
        messagesData.add(assistantToolCallMsgData);

        // 4. Message with role TOOL and ToolCallResultMessage content
        ToolCallResultMessage trContent = new ToolCallResultMessage("tc1", "get_weather", "{\"temp\": \"25C\"}");
        Map<String, Object> toolResultMsgData = new HashMap<>();
        toolResultMsgData.put("role", "TOOL"); // Our enum
        toolResultMsgData.put("content", objectMapper.convertValue(trContent, Map.class));
        toolResultMsgData.put("content_type", "ToolCallResultMessage");
        // No message_class_type, should default to base Message
        messagesData.add(toolResultMsgData);

        // 5. Message with attachments
        Map<String, Object> userMsgWithAttachmentData = new HashMap<>();
        userMsgWithAttachmentData.put("role", "USER");
        userMsgWithAttachmentData.put("content", "See this image.");
        userMsgWithAttachmentData.put("message_class_type", "UserMessage");
        List<Map<String,Object>> attachments = new ArrayList<>();
        Map<String, Object> imageAttachment = new HashMap<>();
        imageAttachment.put("type", "IMAGE");
        imageAttachment.put("content", "base64data==");
        imageAttachment.put("contentType", "BASE64");
        imageAttachment.put("mediaType", "image/png");
        imageAttachment.put("attachment_class_type", "Image"); // Hint for specific attachment type
        attachments.add(imageAttachment);
        userMsgWithAttachmentData.put("attachments", attachments);
        messagesData.add(userMsgWithAttachmentData);


        List<Message> deserialized = chatHistory.deserializeMessages(messagesData);
        assertEquals(5, deserialized.size());

        // Verify UserMessage
        assertTrue(deserialized.get(0) instanceof UserMessage);
        assertEquals(MessageRole.USER, deserialized.get(0).getRole());
        assertEquals("Hello there!", deserialized.get(0).getContent());

        // Verify AssistantMessage
        assertTrue(deserialized.get(1) instanceof AssistantMessage);
        assertEquals(MessageRole.ASSISTANT, deserialized.get(1).getRole());
        assertEquals("Hi! How can I help?", deserialized.get(1).getContent());
        assertNotNull(deserialized.get(1).getUsage());
        assertEquals(15, deserialized.get(1).getUsage().totalTokens());

        // Verify AssistantMessage with ToolCallMessage
        assertTrue(deserialized.get(2) instanceof AssistantMessage);
        assertEquals(MessageRole.ASSISTANT, deserialized.get(2).getRole());
        assertTrue(deserialized.get(2).getContent() instanceof ToolCallMessage);
        ToolCallMessage deserializedTcContent = (ToolCallMessage) deserialized.get(2).getContent();
        assertEquals("tcm1", deserializedTcContent.id());
        assertEquals(1, deserializedTcContent.toolCalls().size());
        assertEquals("get_weather", deserializedTcContent.toolCalls().get(0).function().name());

        // Verify Message with role TOOL and ToolCallResultMessage content
        assertFalse(deserialized.get(3) instanceof UserMessage || deserialized.get(3) instanceof AssistantMessage); // Base Message
        assertEquals(MessageRole.TOOL, deserialized.get(3).getRole());
        assertTrue(deserialized.get(3).getContent() instanceof ToolCallResultMessage);
        ToolCallResultMessage deserializedTrContent = (ToolCallResultMessage) deserialized.get(3).getContent();
        assertEquals("tc1", deserializedTrContent.toolCallId());
        assertEquals("get_weather", deserializedTrContent.name());
        assertEquals("{\"temp\": \"25C\"}", deserializedTrContent.content());

        // Verify UserMessage with Attachment
        assertTrue(deserialized.get(4) instanceof UserMessage);
        assertEquals("See this image.", deserialized.get(4).getContent());
        assertNotNull(deserialized.get(4).getAttachments());
        assertEquals(1, deserialized.get(4).getAttachments().size());
        Attachment attachment = deserialized.get(4).getAttachments().get(0);
        assertTrue(attachment instanceof Image);
        assertEquals(AttachmentType.IMAGE, attachment.getType());
        assertEquals("base64data==", attachment.getContent());
        assertEquals(AttachmentContentType.BASE64, attachment.getContentType());
        assertEquals("image/png", attachment.getMediaType());
    }

    @Test
    void deserializeMessages_malformedRole_throwsIllegalArgumentException() {
        Map<String, Object> malformedRoleMsg = new HashMap<>();
        malformedRoleMsg.put("role", "INVALID_ROLE"); // This will fail MessageRole.valueOf()
        malformedRoleMsg.put("content", "test");

        List<Map<String, Object>> messagesData = List.of(malformedRoleMsg);
        assertThrows(IllegalArgumentException.class, () -> {
            chatHistory.deserializeMessages(messagesData);
        });
    }

    @Test
    void deserializeMessages_contentNotMapForToolCall_leavesAsIsOrFailsSoftly() {
        // If content_type hint is ToolCallMessage but content is not a Map
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("role", "ASSISTANT");
        msgData.put("content", "This should be a map for ToolCallMessage");
        msgData.put("content_type", "ToolCallMessage");
        msgData.put("message_class_type", "AssistantMessage");

        List<Map<String, Object>> messagesData = List.of(msgData);
        List<Message> deserialized = chatHistory.deserializeMessages(messagesData);

        // Current logic will leave content as the original string if conversion to ToolCallMessage fails due to wrong type.
        assertEquals("This should be a map for ToolCallMessage", deserialized.get(0).getContent());
        assertTrue(deserialized.get(0) instanceof AssistantMessage);
    }

    // TODO: Test cutHistoryToContextWindow with token counting if that gets implemented
    // For now, it uses message count as per AbstractChatHistory's current implementation.
}

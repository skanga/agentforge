package com.skanga.chat.messages;

import com.skanga.chat.enums.MessageRole;
import com.skanga.core.Usage;
import com.skanga.core.messages.ToolCallMessage; // For testing content type
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;

class AssistantMessageTests {

    @Test
    void constructor_withContent_setsRoleToAssistant() {
        String content = "This is an assistant message.";
        AssistantMessage assistantMessage = new AssistantMessage(content);

        assertEquals(MessageRole.ASSISTANT, assistantMessage.getRole());
        assertEquals(content, assistantMessage.getContent());
        assertNull(assistantMessage.getUsage());
    }

    @Test
    void constructor_withContentAndUsage_setsRoleAndUsage() {
        String content = "Assistant response with usage.";
        Usage usage = new Usage(10, 20, 30);
        AssistantMessage assistantMessage = new AssistantMessage(content, usage);

        assertEquals(MessageRole.ASSISTANT, assistantMessage.getRole());
        assertEquals(content, assistantMessage.getContent());
        assertEquals(usage, assistantMessage.getUsage());
    }

    @Test
    void defaultConstructor_setsRoleToAssistant() {
        AssistantMessage assistantMessage = new AssistantMessage();
        // The default constructor of AssistantMessage explicitly sets the role.
        assertEquals(MessageRole.ASSISTANT, assistantMessage.getRole());
        assertNull(assistantMessage.getContent());
        assertNull(assistantMessage.getUsage());
    }

    @Test
    void constructor_withToolCallMessageContent_setsCorrectly() {
        ToolCallMessage.FunctionCall funcCall = new ToolCallMessage.FunctionCall("get_weather", "{\"location\":\"London\"}");
        ToolCallMessage.ToolCall toolCall = new ToolCallMessage.ToolCall("tc_123", "function", funcCall);
        ToolCallMessage toolCallMessage = new ToolCallMessage("tcm_abc", Collections.singletonList(toolCall));

        AssistantMessage assistantMessage = new AssistantMessage(toolCallMessage);

        assertEquals(MessageRole.ASSISTANT, assistantMessage.getRole());
        assertSame(toolCallMessage, assistantMessage.getContent()); // Content is the ToolCallMessage object
        assertTrue(assistantMessage.getContent() instanceof ToolCallMessage);
        assertEquals(1, ((ToolCallMessage) assistantMessage.getContent()).toolCalls().size());
    }


    @Test
    void inheritedMethods_workAsExpected() {
        AssistantMessage assistantMessage = new AssistantMessage("Initial content");

        assistantMessage.setContent("Updated assistant content");
        assertEquals("Updated assistant content", assistantMessage.getContent());

        assertEquals(MessageRole.ASSISTANT, assistantMessage.getRole());

        Usage newUsage = new Usage(1,1,2);
        assistantMessage.setUsage(newUsage);
        assertEquals(newUsage, assistantMessage.getUsage());
    }
}

package com.skanga.core.messages;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolCallResultMessageTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void constructor_main_initializesCorrectly() {
        String toolCallId = "call_123";
        String role = "tool";
        String name = "get_weather";
        String content = "{\"temperature\": 22, \"unit\": \"celsius\"}";

        ToolCallResultMessage resultMessage = new ToolCallResultMessage(toolCallId, role, name, content);

        assertEquals(toolCallId, resultMessage.toolCallId());
        assertEquals(role, resultMessage.role());
        assertEquals(name, resultMessage.name());
        assertEquals(content, resultMessage.content());
    }

    @Test
    void constructor_convenience_initializesWithRoleTool() {
        String toolCallId = "call_456";
        String name = "send_email";
        String content = "{\"status\": \"success\", \"message_id\": \"msg_789\"}";

        ToolCallResultMessage resultMessage = new ToolCallResultMessage(toolCallId, name, content);

        assertEquals(toolCallId, resultMessage.toolCallId());
        assertEquals("tool", resultMessage.role()); // Verify default role
        assertEquals(name, resultMessage.name());
        assertEquals(content, resultMessage.content());
    }

    @Test
    void constructor_nullFields_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new ToolCallResultMessage(null, "tool", "name", "content"));
        assertThrows(NullPointerException.class, () -> new ToolCallResultMessage("id", null, "name", "content"));
        assertThrows(NullPointerException.class, () -> new ToolCallResultMessage("id", "tool", null, "content"));
        assertThrows(NullPointerException.class, () -> new ToolCallResultMessage("id", "tool", "name", null));
    }

    @Test
    void constructor_roleNotTool_doesNotThrowButIsUnusual() {
        // The record's canonical constructor has a check, but it's a System.err.println, not an exception.
        // This test just ensures it constructs. The warning would appear in stderr during test run.
        ToolCallResultMessage resultMessage = new ToolCallResultMessage("id", "user", "name", "content");
        assertEquals("user", resultMessage.role());
        // In a real scenario, this might be undesirable, but the record itself permits it.
        // The expectation is that it's wrapped in a Message with MessageRole.TOOL.
    }


    @Test
    void jsonSerialization_serializesCorrectly() throws Exception {
        ToolCallResultMessage resultMessage = new ToolCallResultMessage("call_123", "get_weather", "{\"temp\": 25}");
        String json = objectMapper.writeValueAsString(resultMessage);

        assertTrue(json.contains("\"tool_call_id\":\"call_123\""));
        assertTrue(json.contains("\"role\":\"tool\""));
        assertTrue(json.contains("\"name\":\"get_weather\""));
        assertTrue(json.contains("\"content\":\"{\\\"temp\\\": 25}\"")); // Content is a JSON string
    }

    @Test
    void jsonDeserialization_deserializesCorrectly() throws Exception {
        String json = "{\"tool_call_id\":\"call_123\",\"role\":\"tool\",\"name\":\"get_weather\",\"content\":\"{\\\"temp\\\": 25}\"}";
        ToolCallResultMessage deserialized = objectMapper.readValue(json, ToolCallResultMessage.class);

        assertEquals("call_123", deserialized.toolCallId());
        assertEquals("tool", deserialized.role());
        assertEquals("get_weather", deserialized.name());
        assertEquals("{\"temp\": 25}", deserialized.content());
    }
}

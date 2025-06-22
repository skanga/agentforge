package com.skanga.core.messages;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ToolCallMessageTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void toolCallMessage_constructionAndAccessors() {
        ToolCallMessage.FunctionCall funcCall = new ToolCallMessage.FunctionCall("get_weather", "{\"location\":\"London\", \"unit\":\"celsius\"}");
        ToolCallMessage.ToolCall toolCall = new ToolCallMessage.ToolCall("call_123", "function", funcCall);
        List<ToolCallMessage.ToolCall> toolCallsList = Collections.singletonList(toolCall);
        ToolCallMessage toolCallMessage = new ToolCallMessage("msg_abc", toolCallsList);

        assertEquals("msg_abc", toolCallMessage.id());
        assertEquals(1, toolCallMessage.toolCalls().size());

        ToolCallMessage.ToolCall retrievedToolCall = toolCallMessage.toolCalls().get(0);
        assertEquals("call_123", retrievedToolCall.id());
        assertEquals("function", retrievedToolCall.type());

        ToolCallMessage.FunctionCall retrievedFuncCall = retrievedToolCall.function();
        assertEquals("get_weather", retrievedFuncCall.name());
        assertEquals("{\"location\":\"London\", \"unit\":\"celsius\"}", retrievedFuncCall.arguments());
    }

    @Test
    void toolCallMessage_nullToolCallsList_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            new ToolCallMessage("msg_id", null);
        });
    }

    @Test
    void toolCall_nullFields_throwsNullPointerException() {
        ToolCallMessage.FunctionCall funcCall = new ToolCallMessage.FunctionCall("name", "args");
        assertThrows(NullPointerException.class, () -> new ToolCallMessage.ToolCall(null, "function", funcCall));
        assertThrows(NullPointerException.class, () -> new ToolCallMessage.ToolCall("id", null, funcCall));
        assertThrows(NullPointerException.class, () -> new ToolCallMessage.ToolCall("id", "function", null));
    }

    @Test
    void functionCall_nullFields_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new ToolCallMessage.FunctionCall(null, "args"));
        assertThrows(NullPointerException.class, () -> new ToolCallMessage.FunctionCall("name", null));
    }

    @Test
    void toolCallMessage_emptyToolCallsList_isAllowed() {
        ToolCallMessage toolCallMessage = new ToolCallMessage("msg_id", Collections.emptyList());
        assertNotNull(toolCallMessage.toolCalls());
        assertTrue(toolCallMessage.toolCalls().isEmpty());
    }

    @Test
    void jsonSerialization_toolCallMessage_serializesCorrectly() throws Exception {
        ToolCallMessage.FunctionCall funcCall = new ToolCallMessage.FunctionCall("get_weather", "{\"location\":\"London\"}");
        ToolCallMessage.ToolCall toolCall = new ToolCallMessage.ToolCall("call_123", "function", funcCall);
        ToolCallMessage toolCallMessage = new ToolCallMessage("msg_abc", Collections.singletonList(toolCall));

        String json = objectMapper.writeValueAsString(toolCallMessage);

        assertTrue(json.contains("\"id\":\"msg_abc\""));
        assertTrue(json.contains("\"tool_calls\":["));
        assertTrue(json.contains("\"id\":\"call_123\""));
        assertTrue(json.contains("\"type\":\"function\""));
        assertTrue(json.contains("\"function\":{"));
        assertTrue(json.contains("\"name\":\"get_weather\""));
        assertTrue(json.contains("\"arguments\":\"{\\\"location\\\":\\\"London\\\"}\"")); // Note escaped JSON string
    }

    @Test
    void jsonDeserialization_toolCallMessage_deserializesCorrectly() throws Exception {
        String json = "{\"id\":\"msg_abc\",\"tool_calls\":[{\"id\":\"call_123\",\"type\":\"function\",\"function\":{\"name\":\"get_weather\",\"arguments\":\"{\\\"location\\\":\\\"London\\\"}\"}}]}";
        ToolCallMessage deserialized = objectMapper.readValue(json, ToolCallMessage.class);

        assertEquals("msg_abc", deserialized.id());
        assertNotNull(deserialized.toolCalls());
        assertEquals(1, deserialized.toolCalls().size());

        ToolCallMessage.ToolCall toolCall = deserialized.toolCalls().get(0);
        assertEquals("call_123", toolCall.id());
        assertEquals("function", toolCall.type());
        assertNotNull(toolCall.function());
        assertEquals("get_weather", toolCall.function().name());
        assertEquals("{\"location\":\"London\"}", toolCall.function().arguments());
    }
}

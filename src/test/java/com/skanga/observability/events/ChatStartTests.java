package com.skanga.observability.events;

import com.skanga.core.messages.MessageRequest;
import com.skanga.chat.messages.Message;
import com.skanga.chat.enums.MessageRole;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ChatStartTests {

    @Test
    void constructor_allArgs_setsFieldsCorrectly() {
        Message userMessage = new Message(MessageRole.USER, "Hello");
        MessageRequest request = new MessageRequest(userMessage);
        Map<String, Object> context = Map.of("agentId", "agent007");

        ChatStart event = new ChatStart(request, context);

        assertSame(request, event.request());
        assertEquals(context, event.agentContext()); // Record constructor makes unmodifiable copy
        assertNotSame(context, event.agentContext());
    }

    @Test
    void constructor_requestOnly_setsRequestAndEmptyContext() {
        Message userMessage = new Message(MessageRole.USER, "Hi");
        MessageRequest request = new MessageRequest(userMessage);

        ChatStart event = new ChatStart(request);

        assertSame(request, event.request());
        assertNotNull(event.agentContext());
        assertTrue(event.agentContext().isEmpty());
    }

    @Test
    void constructor_nullRequest_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            new ChatStart(null, Collections.emptyMap());
        });
         assertThrows(NullPointerException.class, () -> {
            new ChatStart(null);
        });
    }

    @Test
    void constructor_nullContext_createsEmptyMap() {
        MessageRequest request = new MessageRequest(new Message(MessageRole.USER, "test"));
        ChatStart event = new ChatStart(request, null);
        assertNotNull(event.agentContext());
        assertTrue(event.agentContext().isEmpty());
    }
}

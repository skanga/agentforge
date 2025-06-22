package com.skanga.observability.events;

import com.skanga.chat.messages.Message;
import com.skanga.chat.enums.MessageRole;
import com.skanga.core.messages.MessageRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatStopTests {

    @Test
    void constructor_allArgs_setsFieldsCorrectly() {
        Message userMessage = new Message(MessageRole.USER, "Question");
        MessageRequest request = new MessageRequest(userMessage);
        Message assistantResponse = new Message(MessageRole.ASSISTANT, "Answer");
        long duration = 1234L;

        ChatStop event = new ChatStop(request, assistantResponse, duration);

        assertSame(request, event.request());
        assertSame(assistantResponse, event.response());
        assertEquals(duration, event.durationMs());
    }

    @Test
    void constructor_nullRequest_throwsNullPointerException() {
        Message assistantResponse = new Message(MessageRole.ASSISTANT, "Answer");
        assertThrows(NullPointerException.class, () -> {
            new ChatStop(null, assistantResponse, 100L);
        });
    }

    @Test
    void constructor_nullResponse_isAllowed() {
        MessageRequest request = new MessageRequest(new Message(MessageRole.USER, "Question"));
        ChatStop event = new ChatStop(request, null, 100L);
        assertNull(event.response());
    }

    @Test
    void constructor_negativeDuration_throwsIllegalArgumentException() {
        MessageRequest request = new MessageRequest(new Message(MessageRole.USER, "Question"));
        Message assistantResponse = new Message(MessageRole.ASSISTANT, "Answer");
        assertThrows(IllegalArgumentException.class, () -> {
            new ChatStop(request, assistantResponse, -5L);
        });
    }

    @Test
    void constructor_zeroDuration_isAllowed() {
        MessageRequest request = new MessageRequest(new Message(MessageRole.USER, "Question"));
        ChatStop event = new ChatStop(request, null, 0L);
        assertEquals(0L, event.durationMs());
    }
}

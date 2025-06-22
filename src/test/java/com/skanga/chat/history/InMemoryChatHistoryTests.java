package com.skanga.chat.history;

import com.skanga.chat.messages.Message;
import com.skanga.chat.enums.MessageRole;
import com.skanga.core.Usage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryChatHistoryTests {

    private InMemoryChatHistory chatHistory;
    private final int contextWindowSize = 3;

    @BeforeEach
    void setUp() {
        chatHistory = new InMemoryChatHistory(contextWindowSize);
    }

    @Test
    void addMessage_addsToHistoryAndRespectsWindow() {
        Message msg1 = new Message(MessageRole.USER, "Hello 1");
        Message msg2 = new Message(MessageRole.ASSISTANT, "Hi 1");
        Message msg3 = new Message(MessageRole.USER, "Hello 2");
        Message msg4 = new Message(MessageRole.ASSISTANT, "Hi 2");

        chatHistory.addMessage(msg1);
        assertEquals(1, chatHistory.getMessages().size());
        assertSame(msg1, chatHistory.getLastMessage());

        chatHistory.addMessage(msg2);
        assertEquals(2, chatHistory.getMessages().size());
        assertSame(msg2, chatHistory.getLastMessage());

        chatHistory.addMessage(msg3);
        assertEquals(3, chatHistory.getMessages().size());
        assertSame(msg3, chatHistory.getLastMessage());

        // Adding msg4 should evict msg1 due to contextWindowSize = 3
        chatHistory.addMessage(msg4);
        assertEquals(3, chatHistory.getMessages().size());
        assertSame(msg4, chatHistory.getLastMessage());
        assertFalse(chatHistory.getMessages().contains(msg1), "Oldest message (msg1) should be evicted.");
        assertTrue(chatHistory.getMessages().contains(msg2));
        assertTrue(chatHistory.getMessages().contains(msg3));
        assertTrue(chatHistory.getMessages().contains(msg4));
    }

    @Test
    void getMessages_returnsCopyOfHistory() {
        Message msg1 = new Message(MessageRole.USER, "Test");
        chatHistory.addMessage(msg1);
        List<Message> messages1 = chatHistory.getMessages();
        assertEquals(1, messages1.size());

        // Modify the returned list and check if original history is affected
        try {
            messages1.add(new Message(MessageRole.USER, "Should not affect original"));
        } catch (UnsupportedOperationException e) {
            // Expected if getMessages() returns an unmodifiable list, which is good practice.
            // AbstractChatHistory.getMessages() returns new ArrayList<>(this.history)
            // so it IS modifiable but won't affect internal.
        }
        assertEquals(1, chatHistory.getMessages().size(), "Original history should not be modified by changes to the returned list.");
    }

    @Test
    void getLastMessage_emptyHistory_returnsNull() {
        assertNull(chatHistory.getLastMessage());
    }

    @Test
    void flushAll_clearsHistory() {
        chatHistory.addMessage(new Message(MessageRole.USER, "Message 1"));
        chatHistory.addMessage(new Message(MessageRole.ASSISTANT, "Message 2"));
        assertFalse(chatHistory.getMessages().isEmpty());

        chatHistory.flushAll();
        assertTrue(chatHistory.getMessages().isEmpty());
        assertNull(chatHistory.getLastMessage());
    }

    @Test
    void calculateTotalUsage_sumsUsageFromMessages() {
        Message msg1 = new Message(MessageRole.USER, "Q1"); // No usage
        Message msg2 = new Message(MessageRole.ASSISTANT, "A1");
        msg2.setUsage(new Usage(10, 20, 30));
        Message msg3 = new Message(MessageRole.USER, "Q2");
        Message msg4 = new Message(MessageRole.ASSISTANT, "A2");
        msg4.setUsage(new Usage(5, 15, 20));

        chatHistory.addMessage(msg1);
        chatHistory.addMessage(msg2);
        chatHistory.addMessage(msg3);
        chatHistory.addMessage(msg4); // This will evict msg1 due to window size 3 (msg2, msg3, msg4 remain)

        // Expected total usage from msg2 and msg4 (msg1 evicted)
        // msg2 has total 30, msg4 has total 20.
        // But only msg2 and msg4 have usage. msg3 doesn't.
        // After eviction of msg1: history is [msg2, msg3, msg4]
        // Usage should be sum of msg2.usage.totalTokens + msg4.usage.totalTokens
        assertEquals(30 + 20, chatHistory.calculateTotalUsage());
    }

    @Test
    void calculateTotalUsage_noUsageInMessages_returnsZero() {
        chatHistory.addMessage(new Message(MessageRole.USER, "No usage here"));
        chatHistory.addMessage(new Message(MessageRole.ASSISTANT, "Me neither"));
        assertEquals(0, chatHistory.calculateTotalUsage());
    }


    @Test
    void cutHistoryToContextWindow_alreadyWithinLimit_noChange() {
        chatHistory.addMessage(new Message(MessageRole.USER, "1"));
        chatHistory.addMessage(new Message(MessageRole.USER, "2"));
        // contextWindowSize is 3
        List<Message> initialMessages = chatHistory.getMessages();
        chatHistory.cutHistoryToContextWindow(); // Should do nothing
        assertEquals(initialMessages, chatHistory.getMessages());
    }

    @Test
    void removeOldestMessage_removesFirstElement() {
        Message msg1 = new Message(MessageRole.USER, "Oldest");
        Message msg2 = new Message(MessageRole.USER, "Newer");
        chatHistory.addMessage(msg1);
        chatHistory.addMessage(msg2);
        assertEquals(2, chatHistory.getMessages().size());

        chatHistory.removeOldestMessage();
        assertEquals(1, chatHistory.getMessages().size());
        assertSame(msg2, chatHistory.getLastMessage());
        assertFalse(chatHistory.getMessages().contains(msg1));
    }

    @Test
    void removeOldestMessage_emptyHistory_doesNothing() {
        assertTrue(chatHistory.getMessages().isEmpty());
        assertDoesNotThrow(() -> chatHistory.removeOldestMessage());
        assertTrue(chatHistory.getMessages().isEmpty());
    }


    @Test
    void toJsonSerializable_returnsListOfMaps() {
        Message msg1 = new Message(MessageRole.USER, "Test content");
        msg1.addMetadata("id", "123");
        chatHistory.addMessage(msg1);

        List<Map<String, Object>> serializable = chatHistory.toJsonSerializable();
        assertNotNull(serializable);
        assertEquals(1, serializable.size());
        Map<String, Object> msgMap = serializable.get(0);
        assertEquals("USER", msgMap.get("role"));
        assertEquals("Test content", msgMap.get("content"));
        assertTrue(msgMap.containsKey("meta"));
        assertEquals("123", ((Map<?,?>)msgMap.get("meta")).get("id"));
    }

    @Test
    void getFreeMemory_calculatesCorrectly() {
        InMemoryChatHistory history = new InMemoryChatHistory(5); // Context window of 5 messages
        assertEquals(5, history.getFreeMemory());
        history.addMessage(new Message(MessageRole.USER, "1"));
        assertEquals(4, history.getFreeMemory());
        history.addMessage(new Message(MessageRole.USER, "2"));
        history.addMessage(new Message(MessageRole.USER, "3"));
        history.addMessage(new Message(MessageRole.USER, "4"));
        history.addMessage(new Message(MessageRole.USER, "5"));
        assertEquals(0, history.getFreeMemory());
        history.addMessage(new Message(MessageRole.USER, "6")); // Evicts one
        assertEquals(0, history.getFreeMemory()); // Still 0 as it's full
    }
}

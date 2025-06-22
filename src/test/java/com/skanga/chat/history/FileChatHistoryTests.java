// ===== Priority Test Classes =====

// 1. FileChatHistoryTests.java
package com.skanga.chat.history;

import com.skanga.chat.enums.MessageRole;
import com.skanga.chat.exceptions.ChatHistoryException;
import com.skanga.chat.messages.Message;
import com.skanga.core.Usage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FileChatHistoryTests {

    @TempDir
    Path tempDir;

    private FileChatHistory fileChatHistory;
    private static final int DEFAULT_CONTEXT_WINDOW = 10;

    @BeforeEach
    void setUp() {
        fileChatHistory = new FileChatHistory(DEFAULT_CONTEXT_WINDOW, tempDir.toString(), "test_session");
    }

    @Test
    void constructor_WithValidParameters_ShouldCreateInstance() {
        // Act & Assert
        assertThat(fileChatHistory).isNotNull();
        assertThat(fileChatHistory.getFilePath()).isNotNull();
        assertThat(Files.exists(fileChatHistory.getFilePath())).isTrue();
    }

    @Test
    void constructor_WithNullDirectory_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new FileChatHistory(5, null, "test"));
    }

    @Test
    void constructor_WithNullSessionId_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new FileChatHistory(5, tempDir.toString(), null));
    }

    @Test
    void constructor_WithZeroContextWindow_ShouldThrowException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                new FileChatHistory(0, tempDir.toString(), "test"));
    }

    @Test
    void addMessage_WithValidMessage_ShouldPersistToFile() throws Exception {
        // Arrange
        Message message = new Message(MessageRole.USER, "Hello, world!");
        message.setUsage(new Usage(10, 20, 30));

        // Act
        fileChatHistory.addMessage(message);

        // Assert
        List<Message> messages = fileChatHistory.getMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("Hello, world!");
        assertThat(messages.get(0).getUsage().totalTokens()).isEqualTo(30);

        // Verify file persistence
        String fileContent = Files.readString(fileChatHistory.getFilePath());
        assertThat(fileContent).contains("Hello, world!");
    }

    @Test
    void addMessage_WithNullMessage_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                fileChatHistory.addMessage(null));
    }

    @Test
    void addMessage_ExceedingContextWindow_ShouldRemoveOldest() {
        // Arrange
        FileChatHistory smallHistory = new FileChatHistory(2, tempDir.toString(), "small_test");

        // Act
        smallHistory.addMessage(new Message(MessageRole.USER, "Message 1"));
        smallHistory.addMessage(new Message(MessageRole.USER, "Message 2"));
        smallHistory.addMessage(new Message(MessageRole.USER, "Message 3"));

        // Assert
        List<Message> messages = smallHistory.getMessages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getContent()).isEqualTo("Message 2");
        assertThat(messages.get(1).getContent()).isEqualTo("Message 3");
    }

    @Test
    void getLastMessage_WithMessages_ShouldReturnLast() {
        // Arrange
        Message message1 = new Message(MessageRole.USER, "First");
        Message message2 = new Message(MessageRole.ASSISTANT, "Second");

        // Act
        fileChatHistory.addMessage(message1);
        fileChatHistory.addMessage(message2);

        // Assert
        Message lastMessage = fileChatHistory.getLastMessage();
        assertThat(lastMessage.getContent()).isEqualTo("Second");
        assertThat(lastMessage.getRole()).isEqualTo(MessageRole.ASSISTANT);
    }

    @Test
    void getLastMessage_WithoutMessages_ShouldReturnNull() {
        // Act
        Message lastMessage = fileChatHistory.getLastMessage();

        // Assert
        assertThat(lastMessage).isNull();
    }

    @Test
    void flushAll_ShouldClearMessagesAndFile() throws Exception {
        // Arrange
        fileChatHistory.addMessage(new Message(MessageRole.USER, "Test message"));
        assertThat(fileChatHistory.getMessages()).hasSize(1);

        // Act
        fileChatHistory.flushAll();

        // Assert
        assertThat(fileChatHistory.getMessages()).isEmpty();
        String fileContent = Files.readString(fileChatHistory.getFilePath());
        assertThat(fileContent).isEmpty();
    }

    @Test
    void calculateTotalUsage_WithMultipleMessages_ShouldSum() {
        // Arrange
        Message message1 = new Message(MessageRole.USER, "Test 1");
        message1.setUsage(new Usage(10, 20, 30));

        Message message2 = new Message(MessageRole.ASSISTANT, "Test 2");
        message2.setUsage(new Usage(15, 25, 40));

        // Act
        fileChatHistory.addMessage(message1);
        fileChatHistory.addMessage(message2);

        // Assert
        int totalUsage = fileChatHistory.calculateTotalUsage();
        assertThat(totalUsage).isEqualTo(70);
    }

    @Test
    void toJsonSerializable_ShouldReturnValidStructure() {
        // Arrange
        Message message = new Message(MessageRole.USER, "Test content");
        message.setUsage(new Usage(5, 10, 15));
        fileChatHistory.addMessage(message);

        // Act
        List<Map<String, Object>> json = fileChatHistory.toJsonSerializable();

        // Assert
        assertThat(json).hasSize(1);
        Map<String, Object> messageJson = json.get(0);
        assertThat(messageJson.get("role")).isEqualTo("USER");
        assertThat(messageJson.get("content")).isEqualTo("Test content");
        assertThat(messageJson).containsKey("usage");
    }

    @Test
    void concurrentAccess_ShouldBeSafe() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        int messagesPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // Act
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < messagesPerThread; j++) {
                        Message message = new Message(MessageRole.USER,
                                String.format("Thread %d, Message %d", threadId, j));
                        fileChatHistory.addMessage(message);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();

        // Due to context window, we might not have all messages
        List<Message> messages = fileChatHistory.getMessages();
        assertThat(messages.size()).isLessThanOrEqualTo(DEFAULT_CONTEXT_WINDOW);

        executor.shutdown();
    }

    @Test
    void persistenceAcrossInstances_ShouldMaintainState() {
        // Arrange
        String sessionId = "persistence_test";
        FileChatHistory history1 = new FileChatHistory(5, tempDir.toString(), sessionId);
        history1.addMessage(new Message(MessageRole.USER, "Persistent message"));

        // Act - Create new instance with same file
        FileChatHistory history2 = new FileChatHistory(5, tempDir.toString(), sessionId);

        // Assert
        List<Message> messages = history2.getMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("Persistent message");
    }

    @Test
    void removeOldestMessage_WithMessages_ShouldRemoveAndUpdateFile() throws Exception {
        // Arrange
        fileChatHistory.addMessage(new Message(MessageRole.USER, "First"));
        fileChatHistory.addMessage(new Message(MessageRole.USER, "Second"));

        // Act
        fileChatHistory.removeOldestMessage();

        // Assert
        List<Message> messages = fileChatHistory.getMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("Second");

        String fileContent = Files.readString(fileChatHistory.getFilePath());
        assertThat(fileContent).doesNotContain("First");
        assertThat(fileContent).contains("Second");
    }

    @Test
    void init_WithInvalidDirectory_ShouldThrowChatHistoryException() {
        // Arrange
        Path invalidPath = tempDir.resolve("non_existent/deeply/nested/path");

        // Act & Assert
        assertThrows(ChatHistoryException.class, () ->
                new FileChatHistory(5, invalidPath.toString(), "test"));
    }
}

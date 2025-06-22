package com.skanga.core;

import com.skanga.chat.enums.MessageRole;
import com.skanga.chat.history.ChatHistory;
import com.skanga.chat.history.InMemoryChatHistory;
import com.skanga.chat.messages.Message;
import com.skanga.core.exceptions.AgentException;
import com.skanga.core.exceptions.ProviderException;
import com.skanga.core.messages.MessageRequest;
import com.skanga.core.messages.ToolCallMessage;
import com.skanga.core.messages.ToolCallResultMessage;
import com.skanga.providers.AIProvider;
import com.skanga.tools.BaseTool;
import com.skanga.tools.Tool;
import com.skanga.tools.ToolExecutionResult;
import com.skanga.tools.properties.PropertyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BaseAgentTests {

    // --- Mocks and Test Subjects ---

    @Mock
    private AIProvider aiProvider;

    @Mock
    private ChatHistory chatHistory;

    @Mock
    private AgentObserver observer;

    private TestableBaseAgent agent;

    // --- Test Setup ---

    @BeforeEach
    void setUp() {
        agent = new TestableBaseAgent();
        agent.withProvider(aiProvider)
                .withChatHistory(chatHistory)
                .addObserver(observer, "*"); // Observe all events for verification
    }

    // Concrete agent implementation for testing abstract BaseAgent
    private static class TestableBaseAgent extends BaseAgent {
        // Expose protected methods for easier testing if needed
        @Override
        public List<ToolCallResultMessage> executeTools(ToolCallMessage toolCallMessage) {
            return super.executeTools(toolCallMessage);
        }
    }

    // Data class for structured responses
    static class TestPerson {
        public String name;
        public int age;
    }

    // Placeholders for observability event data
    record ChatStart(MessageRequest request) {}
    record ChatStop(Message response) {}
    record InstructionsChanged(String newInstructions) {}
    record MessageSaving(Message message) {}
    record MessageSaved(Message message) {}
    record InferenceStart(List<Message> messages, String instructions) {}
    record InferenceStop(Message response) {}
    record AgentError(Exception exception) {}
    record ToolCalling(ToolCallMessage.ToolCall toolCall) {}
    record ToolCalled(ToolCallResultMessage result) {}


    // --- Constructor and Configuration Tests ---

    @Test
    void constructor_initializesWithEmptyState() {
        TestableBaseAgent newAgent = new TestableBaseAgent();
        assertThat(newAgent.getTools()).isNotNull().isEmpty();
        assertThat(newAgent.getInstructions()).isNull();

        // Provider and ChatHistory are not set by default
        assertThrows(AgentException.class, newAgent::resolveProvider);
        assertThrows(AgentException.class, newAgent::resolveChatHistory);
    }

    @Test
    void withProvider_shouldSetProvider() {
        // Arrange
        AIProvider newProvider = mock(AIProvider.class);

        // Act
        Agent result = agent.withProvider(newProvider);

        // Assert
        assertThat(result).isSameAs(agent);
        assertThat(agent.resolveProvider()).isSameAs(newProvider);
    }

    @Test
    void withProvider_withNull_shouldThrowException() {
        assertThrows(NullPointerException.class, () -> agent.withProvider(null));
    }

    @Test
    void resolveProvider_withoutProviderSet_shouldThrowException() {
        // Arrange
        TestableBaseAgent agentWithoutProvider = new TestableBaseAgent();

        // Act & Assert
        AgentException exception = assertThrows(AgentException.class, agentWithoutProvider::resolveProvider);
        assertThat(exception.getMessage()).contains("AIProvider has not been set");
    }

    @Test
    void withInstructions_shouldSetInstructionsAndNotifyObserver() {
        // Arrange
        String instructions = "You are a helpful assistant.";

        // Act
        Agent result = agent.withInstructions(instructions);

        // Assert
        assertThat(result).isSameAs(agent);
        assertThat(agent.getInstructions()).isEqualTo(instructions);
        assertThat(agent.resolveInstructions()).isEqualTo(instructions);
        verify(observer).update(eq("instructions-changed"), isA(InstructionsChanged.class));
    }

    @Test
    void resolveInstructions_whenNoInstructionsSet_returnsDefault() {
        // The default instruction is "You are a helpful assistant."
        assertThat(agent.resolveInstructions()).isEqualTo("You are a helpful assistant.");
    }

    @Test
    void withChatHistory_shouldSetChatHistory() {
        // Arrange
        ChatHistory newHistory = new InMemoryChatHistory(5);

        // Act
        Agent result = agent.withChatHistory(newHistory);

        // Assert
        assertThat(result).isSameAs(agent);
        assertThat(agent.resolveChatHistory()).isSameAs(newHistory);
    }

    @Test
    void resolveChatHistory_withoutHistorySet_shouldThrowException() {
        // Arrange
        TestableBaseAgent agentWithoutHistory = new TestableBaseAgent();

        // Act & Assert
        AgentException exception = assertThrows(AgentException.class, agentWithoutHistory::resolveChatHistory);
        assertThat(exception.getMessage()).contains("ChatHistory has not been set");
    }

    // --- Tool Management Tests ---

    @Test
    void addTool_shouldAddToolToSetAndNotifyObserver() {
        // Arrange
        Tool tool = createTestTool("test_function");

        // Act
        Agent result = agent.addTool(tool);

        // Assert
        assertThat(result).isSameAs(agent);
        assertThat(agent.getTools()).contains(tool);
        verify(observer).update("tool-added", tool);
    }

    @Test
    void bootstrapTools_shouldReturnCurrentListOfTools() {
        // Arrange
        Tool tool1 = createTestTool("tool1");
        Tool tool2 = createTestTool("tool2");
        agent.addTool(tool1);
        agent.addTool(tool2);

        // Act
        List<Object> bootstrapped = agent.bootstrapTools();

        // Assert
        assertThat(bootstrapped).hasSize(2).contains(tool1, tool2);
    }

    // --- Observer Management Tests ---

    @Test
    void addAndRemoveObserver_shouldWorkCorrectly() {
        // Arrange
        AgentObserver newObserver = mock(AgentObserver.class);
        agent.addObserver(newObserver, "*");

        // Act: Trigger an event
        agent.withInstructions("test 1");

        // Assert: New observer was notified
        verify(newObserver).update(eq("instructions-changed"), any());

        // Arrange: Remove observer
        agent.removeObserver(newObserver);

        // Act: Trigger another event
        agent.withInstructions("test 2");

        // Assert: Observer was not notified after removal
        verifyNoMoreInteractions(newObserver);
    }

    @Test
    void removeAllObservers_shouldRemoveAllObservers() {
        // Arrange
        AgentObserver observer2 = mock(AgentObserver.class);
        agent.addObserver(observer, "*");
        agent.addObserver(observer2, "*");

        // Act
        agent.removeAllObservers();
        agent.withInstructions("test");

        // Assert
        verify(observer, never()).update(any(), any());
        verify(observer2, never()).update(any(), any());
    }

    @Test
    void close_shouldCleanupResourcesAndRemoveObservers() {
        // Act
        agent.close();

        // Assert: Trigger an event to verify observers were removed
        agent.withInstructions("test");
        verify(observer, never()).update(any(), any());
    }

    // --- Core Method Tests: chat() ---

    @Test
    void chat_simpleTextResponse_shouldFollowSuccessfulFlowAndNotify() {
        // Arrange
        Message userInput = new Message(MessageRole.USER, "Hello");
        MessageRequest request = new MessageRequest(userInput);
        Message assistantResponse = new Message(MessageRole.ASSISTANT, "Hi there!");
        assistantResponse.setUsage(new Usage(10, 5, 15)); // Assuming Message has setUsage

        when(chatHistory.getMessages()).thenReturn(List.of(userInput));
        when(aiProvider.chatAsync(anyList(), anyString(), anyList()))
                .thenReturn(CompletableFuture.completedFuture(assistantResponse));

        // Act
        Message finalResponse = agent.chat(request);

        // Assert
        assertThat(finalResponse.getContent()).isEqualTo("Hi there!");
        assertThat(finalResponse.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(finalResponse.getUsage()).isNotNull();
        assertThat(finalResponse.getUsage().totalTokens()).isEqualTo(15);

        // Verify interactions and notifications in the correct order
        // The side-effect of `fillChatHistory` is verified by checking `chatHistory.addMessage`
        InOrder inOrder = inOrder(observer, chatHistory, aiProvider);
        inOrder.verify(observer).update(eq("chat-start"), isA(ChatStart.class));
        inOrder.verify(chatHistory).addMessage(userInput);
        inOrder.verify(observer).update(eq("message-saving"), isA(MessageSaving.class));
        inOrder.verify(observer).update(eq("message-saved"), isA(MessageSaved.class));

        inOrder.verify(observer).update(eq("inference-start"), isA(InferenceStart.class));
        inOrder.verify(aiProvider).chatAsync(eq(List.of(userInput)), eq(agent.resolveInstructions()), anyList());
        inOrder.verify(observer).update(eq("inference-stop"), isA(InferenceStop.class));

        inOrder.verify(observer).update(eq("message-saving"), argThat(arg -> ((MessageSaving)arg).message() == assistantResponse));
        inOrder.verify(chatHistory).addMessage(assistantResponse);
        inOrder.verify(observer).update(eq("message-saved"), argThat(arg -> ((MessageSaved)arg).message() == assistantResponse));

        inOrder.verify(observer).update(eq("chat-stop"), isA(ChatStop.class));
    }

    @Test
    void chat_withToolCall_shouldExecuteToolAndContinueChat() {
        // Arrange
        Tool testTool = createTestTool("test_function");
        agent.addTool(testTool);

        MessageRequest request = new MessageRequest(new Message(MessageRole.USER, "Use the test function"));

        // Step 1: LLM responds with a tool call
        ToolCallMessage.ToolCall toolCall = new ToolCallMessage.ToolCall(
                "call-123", "function",
                new ToolCallMessage.FunctionCall("test_function", "{\"param\":\"a-value\"}")
        );
        ToolCallMessage toolCallMessage = new ToolCallMessage("msg-123", List.of(toolCall));
        Message toolCallResponse = new Message(MessageRole.ASSISTANT, toolCallMessage);

        // Step 2: LLM responds with the final text answer after getting the tool result
        Message finalResponse = new Message(MessageRole.ASSISTANT, "Tool executed successfully.");

        when(aiProvider.chatAsync(anyList(), anyString(), anyList()))
                .thenReturn(CompletableFuture.completedFuture(toolCallResponse)) // First call
                .thenReturn(CompletableFuture.completedFuture(finalResponse));  // Second call

        // Act
        Message result = agent.chat(request);

        // Assert
        assertThat(result).isEqualTo(finalResponse);
        verify(aiProvider, times(2)).chatAsync(anyList(), anyString(), anyList());

        // Verify that the tool result was added to history for the second call
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(chatHistory, times(3)).addMessage(messageCaptor.capture());

        List<Message> capturedMessages = messageCaptor.getAllValues();
        assertThat(capturedMessages.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(capturedMessages.get(1).getContent()).isInstanceOf(ToolCallMessage.class);
        assertThat(capturedMessages.get(2).getRole()).isEqualTo(MessageRole.TOOL);
        assertThat(((ToolCallResultMessage) capturedMessages.get(2).getContent()).toolCallId()).isEqualTo("call-123");
    }

    @Test
    void chat_whenProviderFails_shouldThrowAgentExceptionAndNotify() {
        // Arrange
        MessageRequest request = new MessageRequest(new Message(MessageRole.USER, "Trigger error"));
        ProviderException providerException = new ProviderException("LLM unavailable");

        when(aiProvider.chatAsync(anyList(), anyString(), anyList()))
                .thenReturn(CompletableFuture.failedFuture(providerException));

        // Act & Assert
        AgentException thrown = assertThrows(AgentException.class, () -> agent.chat(request));
        assertThat(thrown.getMessage()).contains("Error in chat");
        assertThat(thrown.getCause()).isEqualTo(providerException);

        // Verify notifications
        ArgumentCaptor<Object> eventDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(observer).update(eq("chat-start"), isA(ChatStart.class));
        verify(observer).update(eq("inference-start"), isA(InferenceStart.class));
        verify(observer).update(eq("error"), eventDataCaptor.capture());
        assertThat(((AgentError)eventDataCaptor.getValue()).exception()).isInstanceOf(ProviderException.class);
        verify(observer).update(eq("chat-stop"), isA(ChatStop.class)); // Should still be called on failure
        verify(observer, never()).update(eq("inference-stop"), any());
    }

    // --- Core Method Tests: chatAsync() ---

    @Test
    void chatAsync_shouldReturnCompletableFutureWithResult() throws ExecutionException, InterruptedException {
        // This test implements the TODO for chatAsync
        // Arrange
        Message userInput = new Message(MessageRole.USER, "Hello");
        MessageRequest request = new MessageRequest(userInput);
        Message assistantResponse = new Message(MessageRole.ASSISTANT, "Hi there!");

        when(aiProvider.chatAsync(anyList(), anyString(), anyList()))
                .thenReturn(CompletableFuture.completedFuture(assistantResponse));

        // Act
        CompletableFuture<Message> futureResult = agent.chatAsync(request);

        // Assert
        assertNotNull(futureResult);
        Message finalResponse = futureResult.get(); // Wait for completion

        assertThat(finalResponse).isEqualTo(assistantResponse);
        verify(aiProvider).chatAsync(anyList(), anyString(), anyList());
        verify(observer).update(eq("chat-start"), any());
        verify(observer).update(eq("chat-stop"), any());
    }

    // --- Core Method Tests: stream() ---

    @Test
    @Disabled("Cannot be implemented without BaseAgent.stream() and AIProvider.stream() definitions")
    void stream_shouldHandleStreamingResponse() {
        // This test implements the TODO for stream()
        // To properly implement this test, we need the method signatures and return types for:
        // 1. `BaseAgent.stream(...)`
        // 2. `AIProvider.stream(...)` (which likely returns a `Flowable`, `Flux`, or `Stream` of message chunks)
        //
        // A hypothetical test would look like this:
        //
        // Arrange
        // MessageRequest request = new MessageRequest(...);
        // Stream<MessageChunk> mockStream = Stream.of(new MessageChunk("Hello"), new MessageChunk(" there!"));
        // when(aiProvider.stream(...)).thenReturn(mockStream);
        //
        // Act
        // Stream<MessageChunk> resultStream = agent.stream(request);
        //
        // Assert
        // List<MessageChunk> chunks = resultStream.collect(Collectors.toList());
        // assertThat(chunks).hasSize(2);
        // verify(observer, times(N)).update("stream-chunk", ...);
        // verify(chatHistory).addMessage(...); // for the final aggregated message
    }


    // --- Core Method Tests: executeTools() ---

    @Test
    void executeTools_withValidToolCall_shouldExecuteAndReturnResults() {
        // Arrange
        agent.addTool(createTestTool("test_function"));
        ToolCallMessage.ToolCall toolCall = new ToolCallMessage.ToolCall(
                "call-123", "function", new ToolCallMessage.FunctionCall("test_function", "{\"param\":\"value\"}")
        );
        ToolCallMessage toolCallMessage = new ToolCallMessage("msg-123", List.of(toolCall));

        // Act
        List<ToolCallResultMessage> results = agent.executeTools(toolCallMessage);

        // Assert
        assertThat(results).hasSize(1);
        ToolCallResultMessage result = results.get(0);
        assertThat(result.toolCallId()).isEqualTo("call-123");
        assertThat(result.name()).isEqualTo("test_function");
        assertThat(result.content()).contains("Tool executed with: value");

        // Verify notifications
        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer).update(eq("tool-calling"), isA(ToolCalling.class));
        inOrder.verify(observer).update(eq("tool-called"), isA(ToolCalled.class));
    }

    @Test
    void executeTools_whenToolNotFound_shouldReturnErrorResult() {
        // Arrange
        ToolCallMessage.ToolCall toolCall = new ToolCallMessage.ToolCall(
                "call-456", "function", new ToolCallMessage.FunctionCall("unknown_tool", "{}")
        );
        ToolCallMessage toolCallMessage = new ToolCallMessage("req-def", List.of(toolCall));

        // Act
        List<ToolCallResultMessage> results = agent.executeTools(toolCallMessage);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).content()).contains("Error: Tool 'unknown_tool' not found or not executable");
        verify(observer).update(eq("error"), isA(AgentError.class));
    }

    @Test
    void executeTools_withInvalidArguments_shouldReturnErrorResult() {
        // Arrange
        agent.addTool(createTestTool("test_function"));
        ToolCallMessage.ToolCall toolCall = new ToolCallMessage.ToolCall(
                "call-789", "function", new ToolCallMessage.FunctionCall("test_function", "{invalid json}")
        );
        ToolCallMessage toolCallMessage = new ToolCallMessage("req-ghi", List.of(toolCall));

        // Act
        List<ToolCallResultMessage> results = agent.executeTools(toolCallMessage);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).content()).contains("Error executing tool 'test_function'");
        verify(observer).update(eq("error"), isA(AgentError.class));
    }

    @Test
    void executeTools_whenToolExecutionFails_shouldReturnErrorResultAndNotify() {
        // Arrange
        Tool faultyTool = createTestTool("faulty_tool");
        // Make the tool's callable throw an exception
        faultyTool.setCallable(input -> { throw new RuntimeException("Tool failed!"); });
        agent.addTool(faultyTool);

        ToolCallMessage.ToolCall toolCall = new ToolCallMessage.ToolCall(
                "call-abc", "function", new ToolCallMessage.FunctionCall("faulty_tool", "{}")
        );
        ToolCallMessage toolCallMessage = new ToolCallMessage("req-jkl", List.of(toolCall));

        // Act
        List<ToolCallResultMessage> results = agent.executeTools(toolCallMessage);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).content()).contains("Error executing tool 'faulty_tool': Tool failed!");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(observer).update(eq("error"), eventCaptor.capture());
        assertThat(((AgentError)eventCaptor.getValue()).exception().getMessage()).contains("Tool failed!");
    }

    // --- Core Method Tests: structured() ---

    @Test
    void structured_withValidJson_shouldReturnParsedObject() {
        // Arrange
        MessageRequest request = new MessageRequest(new Message(MessageRole.USER, "Generate a person"));
        String jsonResponse = "{\"name\":\"John\",\"age\":30}";
        Message response = new Message(MessageRole.ASSISTANT, jsonResponse);
        when(aiProvider.chatAsync(anyList(), anyString(), anyList()))
                .thenReturn(CompletableFuture.completedFuture(response));

        // Act
        TestPerson result = agent.structured(request, TestPerson.class, 3);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.name).isEqualTo("John");
        assertThat(result.age).isEqualTo(30);
        verify(observer).update(eq("chat-start"), any());
        verify(observer).update(eq("chat-stop"), any());
    }

    @Test
    void structured_withInvalidJson_shouldRetryAndThrow() {
        // Arrange
        MessageRequest request = new MessageRequest(new Message(MessageRole.USER, "Generate invalid JSON"));
        Message invalidResponse = new Message(MessageRole.ASSISTANT, "{invalid json}");
        when(aiProvider.chatAsync(anyList(), anyString(), anyList()))
                .thenReturn(CompletableFuture.completedFuture(invalidResponse));

        // Act & Assert
        AgentException exception = assertThrows(AgentException.class, () ->
                agent.structured(request, TestPerson.class, 2)); // Retry once (total 2 attempts)

        assertThat(exception.getMessage()).contains("Max retries reached (2) for structured response");
        // Should have called the provider twice (initial + 1 retry)
        verify(aiProvider, times(2)).chatAsync(anyList(), anyString(), anyList());
    }

    // --- Utility Method Tests ---

    @Test
    void removeDelimitedContent_shouldWorkCorrectly() {
        // Arrange
        String text1 = "Hello <TAG>content</TAG> world!";
        String text2 = "No tags here.";
        String text3 = "A<TAG>B</TAG>C<TAG>D</TAG>E";

        // Act & Assert
        assertThat(BaseAgent.removeDelimitedContent(text1, "<TAG>", "</TAG>")).isEqualTo("Hello  world!");
        assertThat(BaseAgent.removeDelimitedContent(text2, "<TAG>", "</TAG>")).isEqualTo("No tags here.");
        assertThat(BaseAgent.removeDelimitedContent(text3, "<TAG>", "</TAG>")).isEqualTo("ACE");
    }

    // --- Private Helper Methods ---

    private Tool createTestTool(String name) {
        BaseTool tool = new BaseTool(name, "A test tool named " + name);
        tool.addParameter("param", PropertyType.STRING, "A test parameter", false);
        tool.setCallable(input -> {
            Object paramValue = input.getArgument("param");
            return new ToolExecutionResult("Tool executed with: " + paramValue);
        });
        return tool;
    }
}
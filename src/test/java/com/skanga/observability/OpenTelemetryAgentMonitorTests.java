
package com.skanga.observability;

import com.skanga.core.Usage;
import com.skanga.chat.messages.Message;
import com.skanga.chat.enums.MessageRole;
import com.skanga.core.messages.MessageRequest;
import com.skanga.core.messages.ToolCallMessage;
import com.skanga.core.messages.ToolCallResultMessage;
import com.skanga.observability.events.*;
import com.skanga.rag.Document;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenTelemetryAgentMonitorTests {

    @Mock
    private Tracer tracer;

    @Mock
    private SpanBuilder spanBuilder;

    @Mock
    private Span span;

    private OpenTelemetryAgentMonitor monitor;

    @BeforeEach
    void setUp() {
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.setParent(any(Context.class))).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        when(span.getSpanContext()).thenReturn(mock(io.opentelemetry.api.trace.SpanContext.class));
        when(span.getSpanContext().isValid()).thenReturn(true);

        monitor = new OpenTelemetryAgentMonitor(tracer);
    }

    @Test
    void constructor_WithValidTracer_ShouldCreateInstance() {
        // Act & Assert
        assertThat(monitor).isNotNull();
    }

    @Test
    void constructor_WithNullTracer_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
            new OpenTelemetryAgentMonitor(null));
    }

    @Test
    void update_WithChatStartEvent_ShouldCreateChatSpan() {
        // Arrange
        MessageRequest request = new MessageRequest(
            Arrays.asList(new Message(MessageRole.USER, "Test message"))
        );
        ChatStart chatStart = new ChatStart(request, Map.of("agent_class", "TestAgent"));

        // Act
        monitor.update("chat-start", chatStart);

        // Assert
        verify(tracer).spanBuilder("AgentChat");
        verify(spanBuilder).startSpan();
        verify(span).setAttribute(eq("jmcp.chat.input.message_count"), eq(1L));
        verify(span).setAttribute(eq("jmcp.agent.ctx.agent_class"), anyString());
    }

    @Test
    void update_WithChatStopEvent_ShouldEndChatSpan() {
        // Arrange
        MessageRequest request = new MessageRequest(
            Arrays.asList(new Message(MessageRole.USER, "Test message"))
        );
        Message response = new Message(MessageRole.ASSISTANT, "Test response");
        response.setUsage(new Usage(10, 20, 30));

        ChatStart chatStart = new ChatStart(request, Map.of());
        ChatStop chatStop = new ChatStop(request, response, 1000L);

        // Act
        monitor.update("chat-start", chatStart);
        monitor.update("chat-stop", chatStop);

        // Assert
        verify(span).setAttribute("jmcp.chat.duration_ms", 1000L);
        verify(span).setAttribute("llm.usage.prompt_tokens", 10L);
        verify(span).setAttribute("llm.usage.completion_tokens", 20L);
        verify(span).setAttribute("llm.usage.total_tokens", 30L);
        verify(span).setStatus(StatusCode.OK);
        verify(span).end();
    }

    @Test
    void update_WithInferenceStartEvent_ShouldCreateInferenceSpan() {
        // Arrange
        List<Message> messages = Arrays.asList(new Message(MessageRole.USER, "Test"));
        InferenceStart inferenceStart = new InferenceStart(
            "openai", "gpt-4", messages, Arrays.asList(), Map.of()
        );

        // Act
        monitor.update("inference-start", inferenceStart);

        // Assert
        verify(tracer).spanBuilder("LLMInference");
        verify(span).setAttribute("llm.system", "openai");
        verify(span).setAttribute("llm.request.model", "gpt-4");
        verify(span).setAttribute("llm.request.message_count", 1L);
    }

    @Test
    void update_WithInferenceStopEvent_ShouldEndInferenceSpan() {
        // Arrange
        Message response = new Message(MessageRole.ASSISTANT, "Response");
        response.setUsage(new Usage(15, 25, 40));

        InferenceStart inferenceStart = new InferenceStart(
            "openai", "gpt-4", Arrays.asList(), Arrays.asList(), Map.of()
        );
        InferenceStop inferenceStop = new InferenceStop(
            "openai", "gpt-4", response, 500L, inferenceStart
        );

        // Act
        monitor.update("inference-start", inferenceStart);
        monitor.update("inference-stop", inferenceStop);

        // Assert
        verify(span).setAttribute("llm.response.duration_ms", 500L);
        verify(span).setAttribute("llm.usage.prompt_tokens", 15L);
        verify(span).setAttribute("llm.usage.completion_tokens", 25L);
        verify(span).setAttribute("llm.usage.total_tokens", 40L);
        verify(span).setStatus(StatusCode.OK);
        verify(span).end();
    }

    @Test
    void update_WithToolCallingEvent_ShouldCreateToolSpans() {
        // Arrange
        ToolCallMessage.ToolCall toolCall = new ToolCallMessage.ToolCall(
            "call-123", "function",
            new ToolCallMessage.FunctionCall("test_function", "{\"param\": \"value\"}")
        );
        ToolCallMessage toolCallMessage = new ToolCallMessage("msg-123", Arrays.asList(toolCall));
        ToolCalling toolCalling = new ToolCalling(toolCallMessage, Map.of());

        // Act
        monitor.update("tool-calling", toolCalling);

        // Assert
        verify(tracer).spanBuilder("ToolExecution: test_function");
        verify(span).setAttribute("tool.name", "test_function");
        verify(span).setAttribute("tool.call_id", "call-123");
        verify(span).setAttribute(eq("tool.arguments"), contains("param"));
    }

    @Test
    void update_WithToolCalledEvent_ShouldEndToolSpans() {
        // Arrange
        ToolCallMessage.ToolCall toolCall = new ToolCallMessage.ToolCall(
            "call-123", "function",
            new ToolCallMessage.FunctionCall("test_function", "{}")
        );
        ToolCallMessage toolCallMessage = new ToolCallMessage("msg-123", Arrays.asList(toolCall));

        ToolCallResultMessage result = new ToolCallResultMessage("call-123", "tool", "test_function", "success");
        ToolCalled toolCalled = new ToolCalled(toolCallMessage, Arrays.asList(result), 100L, Map.of());

        // Act
        monitor.update("tool-calling", toolCall);
        monitor.update("tool-called", toolCalled);

        // Assert
        verify(span).setAttribute(eq("tool.result.content_summary"), contains("success"));
        verify(span).setAttribute("tool.execution.batch_duration_ms", 100L);
        verify(span).setStatus(StatusCode.OK);
        verify(span).end();
    }

    @Test
    void update_WithVectorStoreSearchingEvent_ShouldCreateVectorStoreSpan() {
        // Arrange
        Message queryMessage = new Message(MessageRole.USER, "search query");
        List<Float> queryEmbedding = Arrays.asList(0.1f, 0.2f, 0.3f);
        Map<String, Object> filter = Map.of("category", "test");
        VectorStoreSearching searching = new VectorStoreSearching(
                "ChromaDB", queryMessage, queryEmbedding, 5, filter);

        // Act
        monitor.update("vector-store-searching", searching);

        // Assert
        verify(tracer).spanBuilder("VectorStoreSearch");
        verify(span).setAttribute("db.system", "vectorstore");
        verify(span).setAttribute("db.operation", "search");
        verify(span).setAttribute("db.vectorstore.name", "ChromaDB");
        verify(span).setAttribute("db.vectorstore.top_k", 5L);
        verify(span).setAttribute(eq("db.vectorstore.query_text"), contains("search query"));
    }

    @Test
    void update_WithVectorStoreResultEvent_ShouldEndVectorStoreSpan() {
        // Arrange
        Message queryMessage = new Message(MessageRole.USER, "search query");
        List<Document> documents = Arrays.asList(
                new Document("doc1"), new Document("doc2")
        );
        List<Float> queryEmbedding = Arrays.asList(0.1f, 0.2f, 0.3f);
        Map<String, Object> filter = Map.of("category", "test");
        VectorStoreSearching searching = new VectorStoreSearching(
                "ChromaDB", queryMessage, queryEmbedding, 5, filter);

        VectorStoreResult result = new VectorStoreResult(
                "ChromaDB", queryMessage, documents, 200L
        );

        // Act
        monitor.update("vector-store-searching", searching);
        monitor.update("vector-store-result", result);

        // Assert
        verify(span).setAttribute("db.vectorstore.result_count", 2L);
        verify(span).setAttribute("db.vectorstore.duration_ms", 200L);
        verify(span).setStatus(StatusCode.OK);
        verify(span).end();
    }

    @Test
    void update_WithInstructionsChangedEvent_ShouldAddEvent() {
        // Arrange
        InstructionsChanged instructionsChanged = new InstructionsChanged(
                "Old instructions", "New instructions", Map.of("reason", "test")
        );

        // Act
        monitor.update("instructions-changed", instructionsChanged);

        // Assert
        verify(span).addEvent(eq("InstructionsChanged"), any(Attributes.class));
    }

    @Test
    void update_WithStructuredOutputEvent_ShouldAddEvent() {
        // Arrange
        Message response = new Message(MessageRole.ASSISTANT, "test response");
        StructuredOutputEvent structuredEvent = StructuredOutputEvent.extracting(response, String.class);

        // Act
        monitor.update("structured-output-event", structuredEvent);

        // Assert
        verify(span).addEvent(eq("StructuredOutputLifecycle"), any(Attributes.class));
    }

    @Test
    void update_WithAgentErrorEvent_ShouldRecordException() {
        // Arrange
        Exception testException = new RuntimeException("Test error");
        AgentError agentError = new AgentError(testException, true, "Test error message", Map.of());

        // Act
        monitor.update("error", agentError);

        // Assert
        verify(span).recordException(testException);
        verify(span).setStatus(eq(StatusCode.ERROR), contains("Test error message"));
        verify(span).setAttribute("error.critical", true);
    }

    @Test
    void update_WithUnknownEvent_ShouldNotThrow() {
        // Act & Assert
        assertDoesNotThrow(() -> monitor.update("unknown-event", "some data"));
    }

    @Test
    void update_WithNullEventType_ShouldNotThrow() {
        // Act & Assert
        assertDoesNotThrow(() -> monitor.update(null, "some data"));
    }

    @Test
    void update_WithInvalidSpan_ShouldHandleGracefully() {
        // Arrange
        when(span.getSpanContext().isValid()).thenReturn(false);
        MessageRequest request = new MessageRequest(
                Arrays.asList(new Message(MessageRole.USER, "Test"))
        );
        ChatStart chatStart = new ChatStart(request, Map.of());

        // Act & Assert
        assertDoesNotThrow(() -> monitor.update("chat-start", chatStart));
    }

    @Test
    void concurrentUpdates_ShouldBeSafe() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        List<Thread> threads = new ArrayList<>();

        // Act
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            Thread thread = new Thread(() -> {
                MessageRequest request = new MessageRequest(
                        Arrays.asList(new Message(MessageRole.USER, "Test " + threadId))
                );
                ChatStart chatStart = new ChatStart(request, Map.of());
                monitor.update("chat-start", chatStart);
            });
            threads.add(thread);
            thread.start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        verify(tracer, times(threadCount)).spanBuilder("AgentChat");
    }
}

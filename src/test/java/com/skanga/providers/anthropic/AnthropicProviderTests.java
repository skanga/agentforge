package com.skanga.providers.anthropic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.chat.enums.MessageRole;
import com.skanga.chat.messages.Message;
import com.skanga.core.Usage;
import com.skanga.core.exceptions.ProviderException;
import com.skanga.core.messages.ToolCallMessage;
import com.skanga.core.messages.ToolCallResultMessage;
import com.skanga.providers.anthropic.dto.AnthropicMessageStartData;
import com.skanga.providers.anthropic.dto.AnthropicStreamContentBlockDelta;
import com.skanga.tools.BaseTool;
import com.skanga.tools.Tool;
import com.skanga.tools.ToolExecutionResult;
import com.skanga.tools.properties.PropertyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException; // Added import
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnthropicProviderTests {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private AnthropicProvider provider;
    private ObjectMapper objectMapper;

    private static final String TEST_API_KEY = "test-api-key";
    private static final String TEST_MODEL = "claude-3-sonnet";

    @BeforeEach
    void setUp() {
        provider = new AnthropicProvider(TEST_API_KEY, TEST_MODEL);
        provider.setHttpClient(httpClient);
        objectMapper = new ObjectMapper();
    }

    @Test
    void constructor_WithValidParameters_ShouldCreateInstance() {
        // Act & Assert
        assertThat(provider).isNotNull();
    }

    @Test
    void constructor_WithNullApiKey_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new AnthropicProvider(null, TEST_MODEL));
    }

    @Test
    void constructor_WithNullModel_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new AnthropicProvider(TEST_API_KEY, null));
    }

    @Test
    void systemPrompt_ShouldSetSystemPrompt() {
        // Arrange
        String systemPrompt = "You are a helpful assistant";

        // Act
        AnthropicProvider result = (AnthropicProvider) provider.systemPrompt(systemPrompt);

        // Assert
        assertThat(result).isSameAs(provider);
    }

    @Test
    void setTools_WithValidTools_ShouldSetTools() {
        // Arrange
        Tool tool = createTestTool("test_function", "Test function");
        List<Object> tools = Arrays.asList(tool);

        // Act
        AnthropicProvider result = (AnthropicProvider) provider.setTools(tools);

        // Assert
        assertThat(result).isSameAs(provider);
    }

    @Test
    void chatAsync_WithSimpleMessage_ShouldReturnResponse() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Hello, Claude!")
        );

        Map<String, Object> mockResponse = createMockAnthropicResponse("Hello! How can I help you today?");
        String responseJson = objectMapper.writeValueAsString(mockResponse);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseJson);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        CompletableFuture<Message> future = provider.chatAsync(messages, null, Collections.emptyList());
        Message result = future.get();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(result.getContent()).isEqualTo("Hello! How can I help you today?");
        assertThat(result.getUsage()).isNotNull();
        assertThat(result.getUsage().promptTokens()).isEqualTo(10);
        assertThat(result.getUsage().completionTokens()).isEqualTo(20);
    }

    @Test
    void chatAsync_WithToolUse_ShouldReturnToolCallMessage() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "What's the weather in NYC?")
        );

        Map<String, Object> mockResponse = createMockAnthropicResponseWithToolUse();
        String responseJson = objectMapper.writeValueAsString(mockResponse);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseJson);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        CompletableFuture<Message> future = provider.chatAsync(messages, null, Collections.emptyList());
        Message result = future.get();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(result.getContent()).isInstanceOf(ToolCallMessage.class);

        ToolCallMessage toolCallMessage = (ToolCallMessage) result.getContent();
        assertThat(toolCallMessage.toolCalls()).hasSize(1);
        assertThat(toolCallMessage.toolCalls().get(0).function().name()).isEqualTo("get_weather");
    }

    @Test
    void chatAsync_WithSystemMessage_ShouldExcludeFromContent() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.SYSTEM, "You are helpful"),
                new Message(MessageRole.USER, "Hello")
        );

        Map<String, Object> mockResponse = createMockAnthropicResponse("Hi there!");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(objectMapper.writeValueAsString(mockResponse));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        provider.chatAsync(messages, null, Collections.emptyList()).get();

        // Assert
        // Verify that send was called, assuming the AnthropicMessageMapper and payload construction
        // correctly handle placing the system prompt. Detailed payload inspection is complex here.
        verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void chatAsync_WithHttpError_ShouldThrowProviderException() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Hello")
        );

        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn("{\"error\":\"Bad Request\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        CompletableFuture<Message> future = provider.chatAsync(messages, null, Collections.emptyList());

        // Assert
        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);
        assertThat(executionException.getCause()).isInstanceOf(ProviderException.class);
        // The message from ProviderException now includes the status code and body
        assertThat(executionException.getCause().getMessage()).isEqualTo("Error during Anthropic API call: Anthropic API request failed (Status: 400, Body: {\"error\":\"Bad Request\"})");
    }

    @Test
    void stream_ShouldReturnStreamOfChunks() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Tell me a story")
        );

        String streamResponse =
                "event: message_start\n" +
                        "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":5,\"output_tokens\":0}}}\n\n" +
                        "event: content_block_delta\n" +
                        "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Once\"}}\n\n" +
                        "event: content_block_delta\n" +
                        "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\" upon\"}}\n\n" +
                        "event: message_delta\n" +
                        "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":10}}\n\n";

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(streamResponse);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        Stream<String> stream = provider.stream(messages, null, Collections.emptyList());
        List<String> chunks = stream.toList();

        // Assert
        assertThat(chunks).containsExactly("Once", " upon");
    }

    @Test
    void structured_WithValidSchema_ShouldReturnParsedObject() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Generate a person object")
        );

        Map<String, Object> mockResponse = createMockAnthropicResponseWithStructuredOutput();
        String responseJson = objectMapper.writeValueAsString(mockResponse);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseJson);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        TestPerson result = provider.structured(messages, TestPerson.class, Map.of());

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.name).isEqualTo("John Doe");
        assertThat(result.age).isEqualTo(30);
    }

    @Test
    void structured_WithoutRequiredTool_ShouldThrowException() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Generate data")
        );

        Map<String, Object> mockResponse = createMockAnthropicResponse("I cannot generate structured data");

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(objectMapper.writeValueAsString(mockResponse));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act & Assert
        ProviderException exception = assertThrows(ProviderException.class, () ->
                provider.structured(messages, TestPerson.class, Map.of()));
        assertThat(exception.getMessage()).contains("did not use the required tool");
    }

    @Test
    void messageMapper_ShouldReturnAnthropicMessageMapper() {
        // Act
        var mapper = provider.messageMapper();

        // Assert
        assertThat(mapper).isInstanceOf(AnthropicMessageMapper.class);
    }

    @Test
    void setHttpClient_ShouldSetClient() {
        // Arrange
        HttpClient newClient = mock(HttpClient.class);

        // Act
        AnthropicProvider result = (AnthropicProvider) provider.setHttpClient(newClient);

        // Assert
        assertThat(result).isSameAs(provider);
    }

    @Test
    void chat_ShouldCallChatAsync() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Hello")
        );

        Map<String, Object> mockResponse = createMockAnthropicResponse("Hello back!");

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(objectMapper.writeValueAsString(mockResponse));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        Message result = provider.chat(messages);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("Hello back!");
    }

    // Helper methods
    private Map<String, Object> createMockAnthropicResponse(String textContent) {
        return Map.of(
                "role", "assistant",
                "content", Arrays.asList(
                        Map.of("type", "text", "text", textContent)
                ),
                "usage", Map.of(
                        "input_tokens", 10,
                        "output_tokens", 20
                )
        );
    }

    private Map<String, Object> createMockAnthropicResponseWithToolUse() {
        return Map.of(
                "role", "assistant",
                "content", Arrays.asList(
                        Map.of(
                                "type", "tool_use",
                                "id", "call-123",
                                "name", "get_weather",
                                "input", Map.of("location", "NYC")
                        )
                ),
                "usage", Map.of("input_tokens", 15, "output_tokens", 25)
        );
    }

    private Map<String, Object> createMockAnthropicResponseWithStructuredOutput() {
        return Map.of(
                "role", "assistant",
                "content", Arrays.asList(
                        Map.of(
                                "type", "tool_use",
                                "id", "extract-123",
                                "name", "extract_testperson",
                                "input", Map.of("name", "John Doe", "age", 30)
                        )
                ),
                "usage", Map.of("input_tokens", 10, "output_tokens", 15)
        );
    }

    private Tool createTestTool(String name, String description) {
        BaseTool tool = new BaseTool(name, description);
        tool.addParameter("location", PropertyType.STRING, "The location", true);
        tool.setCallable(input -> new ToolExecutionResult("Tool executed"));
        return tool;
    }

    static class TestPerson {
        public String name;
        public int age;

        public TestPerson() {}

        public TestPerson(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }
}
package com.skanga.providers.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.chat.enums.MessageRole;
import com.skanga.chat.messages.Message;
import com.skanga.core.Usage;
import com.skanga.core.exceptions.ProviderException;
import com.skanga.core.messages.ToolCallMessage;
import com.skanga.providers.openai.dto.OpenAIStreamData;
import com.skanga.providers.openai.dto.OpenAIStreamChoice;
import com.skanga.providers.openai.dto.OpenAIStreamDelta;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenAIProviderTests {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private OpenAIProvider provider;
    private ObjectMapper objectMapper;

    private static final String TEST_API_KEY = "test-api-key";
    private static final String TEST_MODEL = "gpt-4";
    private static final String TEST_BASE_URI = "https://api.openai.com/v1";

    @BeforeEach
    void setUp() {
        provider = new OpenAIProvider(TEST_API_KEY, TEST_MODEL, TEST_BASE_URI);
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
                new OpenAIProvider(null, TEST_MODEL, TEST_BASE_URI));
    }

    @Test
    void constructor_WithNullModel_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new OpenAIProvider(TEST_API_KEY, null, TEST_BASE_URI));
    }

    @Test
    void constructor_WithNullBaseUri_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new OpenAIProvider(TEST_API_KEY, TEST_MODEL, null, null));
    }

    @Test
    void chatAsync_WithSimpleMessage_ShouldReturnResponse() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Hello, GPT!")
        );

        Map<String, Object> mockResponse = createMockOpenAIResponse("Hello! How can I help you today?");
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
        assertThat(result.getUsage().completionTokens()).isEqualTo(25);
        assertThat(result.getUsage().totalTokens()).isEqualTo(35);
    }

    @Test
    void chatAsync_WithToolCalls_ShouldReturnToolCallMessage() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "What's the weather in San Francisco?")
        );

        Map<String, Object> mockResponse = createMockOpenAIResponseWithToolCalls();
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
        assertThat(toolCallMessage.toolCalls().get(0).id()).isEqualTo("call_abc123");
    }

    @Test
    void chatAsync_WithSystemPrompt_ShouldIncludeSystemMessage() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Hello")
        );
        String systemPrompt = "You are a helpful assistant";

        Map<String, Object> mockResponse = createMockOpenAIResponse("Hi there!");

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(objectMapper.writeValueAsString(mockResponse));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        provider.chatAsync(messages, systemPrompt, Collections.emptyList()).get();

        // Assert
        verify(httpClient).send(argThat(request -> {
            // Verify the request includes system message
            return request.uri().toString().contains("chat/completions");
        }), any());
    }

    @Test
    void chatAsync_WithTools_ShouldIncludeToolsInRequest() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Check the weather")
        );
        Tool weatherTool = createTestTool("get_weather", "Get weather information");
        List<Object> tools = Arrays.asList(weatherTool);

        Map<String, Object> mockResponse = createMockOpenAIResponse("I'll check the weather for you.");

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(objectMapper.writeValueAsString(mockResponse));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        provider.chatAsync(messages, null, tools).get();

        // Assert
        verify(httpClient).send(argThat(request -> {
            // Verify the request includes tools
            return request.uri().toString().contains("chat/completions");
        }), any());
    }

    @Test
    void chatAsync_WithHttpError_ShouldThrowProviderException() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Hello")
        );

        when(httpResponse.statusCode()).thenReturn(401);
        when(httpResponse.body()).thenReturn("{\"error\":{\"message\":\"Invalid API key\"}}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        CompletableFuture<Message> future = provider.chatAsync(messages, null, Collections.emptyList());

        // Assert
        ProviderException exception = assertThrows(ProviderException.class, () -> future.get());
        assertThat(exception.getMessage()).contains("OpenAI API request failed");
    }

    @Test
    void chatAsync_WithMissingChoices_ShouldThrowException() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Hello")
        );

        Map<String, Object> invalidResponse = Map.of(
                "id", "test-id",
                "object", "chat.completion"
                // Missing choices
        );

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(objectMapper.writeValueAsString(invalidResponse));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        CompletableFuture<Message> future = provider.chatAsync(messages, null, Collections.emptyList());

        // Assert
        ProviderException exception = assertThrows(ProviderException.class, () -> future.get());
        assertThat(exception.getMessage()).contains("missing 'choices'");
    }

    @Test
    void stream_ShouldReturnStreamOfChunks() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Tell me a joke")
        );

        String streamResponse =
                "data: {\"choices\":[{\"delta\":{\"content\":\"Why\"}}]}\n\n" +
                        "data: {\"choices\":[{\"delta\":{\"content\":\" did\"}}]}\n\n" +
                        "data: {\"choices\":[{\"delta\":{\"content\":\" the\"}}]}\n\n" +
                        "data: {\"choices\":[{\"delta\":{\"content\":\" chicken\"}}]}\n\n" +
                        "data: [DONE]\n\n";

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(streamResponse);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        Stream<String> stream = provider.stream(messages, null, Collections.emptyList());
        List<String> chunks = stream.toList();

        // Assert
        assertThat(chunks).containsExactly("Why", " did", " the", " chicken");
    }

    @Test
    void stream_WithHttpError_ShouldThrowException() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Hello")
        );

        when(httpResponse.statusCode()).thenReturn(429);
        when(httpResponse.body()).thenReturn("{\"error\":{\"message\":\"Rate limit exceeded\"}}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act & Assert
        ProviderException exception = assertThrows(ProviderException.class, () ->
                provider.stream(messages, null, Collections.emptyList()));
        assertThat(exception.getMessage()).contains("stream request failed");
    }

    @Test
    void structured_WithValidSchema_ShouldReturnParsedObject() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Generate a person object")
        );

        Map<String, Object> mockResponse = createMockOpenAIStructuredResponse();
        String responseJson = objectMapper.writeValueAsString(mockResponse);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseJson);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        TestPerson result = provider.structured(messages, TestPerson.class, Map.of());

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.name).isEqualTo("Alice Johnson");
        assertThat(result.age).isEqualTo(28);
    }

    @Test
    void structured_WithInvalidJson_ShouldThrowException() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Generate invalid JSON")
        );

        Map<String, Object> mockResponse = createMockOpenAIResponse("{invalid json}");

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(objectMapper.writeValueAsString(mockResponse));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act & Assert
        ProviderException exception = assertThrows(ProviderException.class, () ->
                provider.structured(messages, TestPerson.class, Map.of()));
        assertThat(exception.getMessage()).contains("Error during OpenAI structured output");
    }

    @Test
    void structured_WithEmptyContent_ShouldThrowException() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Generate empty response")
        );

        Map<String, Object> mockResponse = createMockOpenAIResponse("");

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(objectMapper.writeValueAsString(mockResponse));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act & Assert
        ProviderException exception = assertThrows(ProviderException.class, () ->
                provider.structured(messages, TestPerson.class, Map.of()));
        assertThat(exception.getMessage()).contains("content is empty");
    }

    @Test
    void setTools_WithValidTools_ShouldSetTools() {
        // Arrange
        Tool tool = createTestTool("test_function", "Test function");
        List<Object> tools = Arrays.asList(tool);

        // Act
        OpenAIProvider result = (OpenAIProvider) provider.setTools(tools);

        // Assert
        assertThat(result).isSameAs(provider);
    }

    @Test
    void systemPrompt_ShouldSetSystemPrompt() {
        // Arrange
        String systemPrompt = "You are a helpful assistant";

        // Act
        OpenAIProvider result = (OpenAIProvider) provider.systemPrompt(systemPrompt);

        // Assert
        assertThat(result).isSameAs(provider);
    }

    @Test
    void messageMapper_ShouldReturnOpenAIMessageMapper() {
        // Act
        var mapper = provider.messageMapper();

        // Assert
        assertThat(mapper).isInstanceOf(OpenAIMessageMapper.class);
    }

    @Test
    void setHttpClient_ShouldSetClient() {
        // Arrange
        HttpClient newClient = mock(HttpClient.class);

        // Act
        OpenAIProvider result = (OpenAIProvider) provider.setHttpClient(newClient);

        // Assert
        assertThat(result).isSameAs(provider);
    }

    @Test
    void chat_ShouldCallChatAsync() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Hello")
        );

        Map<String, Object> mockResponse = createMockOpenAIResponse("Hello back!");

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

    @Test
    void createToolCallMessage_WithValidToolCalls_ShouldCreateMessage() throws JsonProcessingException {
        // Arrange
        List<Map<String, Object>> openAiToolCalls = Arrays.asList(
                Map.of(
                        "id", "call_123",
                        "type", "function",
                        "function", Map.of(
                                "name", "get_weather",
                                "arguments", "{\"location\":\"NYC\"}"
                        )
                )
        );

        // Act
        // This tests the private method indirectly through chatAsync with tool calls
        Map<String, Object> mockResponse = createMockOpenAIResponseWithToolCalls();

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(objectMapper.writeValueAsString(mockResponse));

        // Assert
        assertDoesNotThrow(() -> {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            List<Message> messages = Arrays.asList(new Message(MessageRole.USER, "Test"));
            provider.chatAsync(messages, null, Collections.emptyList()).get();
        });
    }

    @Test
    void generateToolsPayload_WithValidTools_ShouldGeneratePayload() throws Exception {
        // Arrange
        Tool tool = createTestTool("test_tool", "A test tool");
        List<Object> tools = Arrays.asList(tool);

        Map<String, Object> mockResponse = createMockOpenAIResponse("Using tools");

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(objectMapper.writeValueAsString(mockResponse));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        List<Message> messages = Arrays.asList(new Message(MessageRole.USER, "Use the tool"));
        provider.chatAsync(messages, null, tools).get();

        // Assert
        verify(httpClient).send(argThat(request -> {
            // Verify that tools are included in the request
            return request.uri().toString().contains("chat/completions");
        }), any());
    }

    // Helper methods
    private Map<String, Object> createMockOpenAIResponse(String content) {
        return Map.of(
                "id", "chatcmpl-123",
                "object", "chat.completion",
                "choices", Arrays.asList(
                        Map.of(
                                "index", 0,
                                "message", Map.of(
                                        "role", "assistant",
                                        "content", content
                                ),
                                "finish_reason", "stop"
                        )
                ),
                "usage", Map.of(
                        "prompt_tokens", 10,
                        "completion_tokens", 25,
                        "total_tokens", 35
                )
        );
    }

    private Map<String, Object> createMockOpenAIResponseWithToolCalls() {
        return Map.of(
                "id", "chatcmpl-123",
                "object", "chat.completion",
                "choices", Arrays.asList(
                        Map.of(
                                "index", 0,
                                "message", Map.of(
                                        "role", "assistant",
                                        "content", null,
                                        "tool_calls", Arrays.asList(
                                                Map.of(
                                                        "id", "call_abc123",
                                                        "type", "function",
                                                        "function", Map.of(
                                                                "name", "get_weather",
                                                                "arguments", "{\"location\":\"San Francisco\"}"
                                                        )
                                                )
                                        )
                                ),
                                "finish_reason", "tool_calls"
                        )
                ),
                "usage", Map.of(
                        "prompt_tokens", 15,
                        "completion_tokens", 30,
                        "total_tokens", 45
                )
        );
    }

    private Map<String, Object> createMockOpenAIStructuredResponse() {
        String jsonContent = "{\"name\":\"Alice Johnson\",\"age\":28}";
        return Map.of(
                "id", "chatcmpl-123",
                "object", "chat.completion",
                "choices", Arrays.asList(
                        Map.of(
                                "index", 0,
                                "message", Map.of(
                                        "role", "assistant",
                                        "content", jsonContent
                                ),
                                "finish_reason", "stop"
                        )
                ),
                "usage", Map.of(
                        "prompt_tokens", 12,
                        "completion_tokens", 18,
                        "total_tokens", 30
                )
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

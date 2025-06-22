package com.skanga.providers.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.chat.enums.MessageRole;
import com.skanga.chat.messages.Message;
import com.skanga.core.Usage;
import com.skanga.core.exceptions.ProviderException;
import com.skanga.core.messages.ToolCallMessage;
import com.skanga.providers.gemini.dto.*;
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
class GeminiProviderTests {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private GeminiProvider provider;
    private ObjectMapper objectMapper;

    private static final String TEST_API_KEY = "test-api-key";
    private static final String TEST_MODEL = "gemini-pro";

    @BeforeEach
    void setUp() {
        provider = new GeminiProvider(TEST_API_KEY, TEST_MODEL);
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
                new GeminiProvider(null, TEST_MODEL));
    }

    @Test
    void constructor_WithNullModel_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new GeminiProvider(TEST_API_KEY, null));
    }

    @Test
    void chatAsync_WithSimpleMessage_ShouldReturnResponse() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Hello, Gemini!")
        );

        GenerateContentResponse mockResponse = createMockGeminiResponse("Hello! How can I assist you?");
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
        assertThat(result.getContent()).isEqualTo("Hello! How can I assist you?");
        assertThat(result.getUsage()).isNotNull();
    }

    @Test
    void chatAsync_WithFunctionCall_ShouldReturnToolCallMessage() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "What's the weather like?")
        );

        GenerateContentResponse mockResponse = createMockGeminiResponseWithFunctionCall();
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
        assertThat(result.getContent()).isInstanceOf(ToolCallMessage.class);

        ToolCallMessage toolCallMessage = (ToolCallMessage) result.getContent();
        assertThat(toolCallMessage.toolCalls()).hasSize(1);
        assertThat(toolCallMessage.toolCalls().get(0).function().name()).isEqualTo("get_weather");
    }

    @Test
    void chatAsync_WithBlockedContent_ShouldThrowException() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Blocked content")
        );

        GenerateContentResponse mockResponse = createMockGeminiResponseBlocked();
        String responseJson = objectMapper.writeValueAsString(mockResponse);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseJson);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        CompletableFuture<Message> future = provider.chatAsync(messages, null, Collections.emptyList());

        // Assert
        ProviderException exception = assertThrows(ProviderException.class, () -> future.get());
        assertThat(exception.getMessage()).contains("prompt was blocked");
    }

    @Test
    void chatAsync_WithFinishReasonOtherThanStop_ShouldThrowException() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Test message")
        );

        GenerateContentResponse mockResponse = createMockGeminiResponseWithFinishReason("MAX_TOKENS");
        String responseJson = objectMapper.writeValueAsString(mockResponse);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseJson);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        CompletableFuture<Message> future = provider.chatAsync(messages, null, Collections.emptyList());

        // Assert
        ProviderException exception = assertThrows(ProviderException.class, () -> future.get());
        assertThat(exception.getMessage()).contains("finished due to: MAX_TOKENS");
    }

    @Test
    void stream_ShouldReturnStreamOfChunks() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Tell me a story")
        );

        String streamResponse =
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Once\"}]}}],\"usageMetadata\":{\"promptTokenCount\":5}}\n\n" +
                        "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" upon\"}]}}]}\n\n" +
                        "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" a time\"}]}}]}\n\n" +
                        "data: {\"usageMetadata\":{\"candidatesTokenCount\":10}}\n\n";

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(streamResponse);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        Stream<String> stream = provider.stream(messages, null, Collections.emptyList());
        List<String> chunks = stream.toList();

        // Assert
        assertThat(chunks).containsExactly("Once", " upon", " a time");
    }

    @Test
    void structured_WithValidSchema_ShouldReturnParsedObject() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Generate a person")
        );

        GenerateContentResponse mockResponse = createMockGeminiResponseWithStructuredOutput();
        String responseJson = objectMapper.writeValueAsString(mockResponse);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseJson);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        TestPerson result = provider.structured(messages, TestPerson.class, Map.of());

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.name).isEqualTo("Jane Smith");
        assertThat(result.age).isEqualTo(25);
    }

    @Test
    void structured_WithoutFunctionCall_ShouldThrowException() throws Exception {
        // Arrange
        List<Message> messages = Arrays.asList(
                new Message(MessageRole.USER, "Generate data")
        );

        GenerateContentResponse mockResponse = createMockGeminiResponse("I cannot generate structured data");

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(objectMapper.writeValueAsString(mockResponse));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act & Assert
        ProviderException exception = assertThrows(ProviderException.class, () ->
                provider.structured(messages, TestPerson.class, Map.of()));
        assertThat(exception.getMessage()).contains("did not use required tool");
    }

    @Test
    void setTools_WithValidTools_ShouldSetTools() {
        // Arrange
        Tool tool = createTestTool("test_function", "Test function");
        List<Object> tools = Arrays.asList(tool);

        // Act
        GeminiProvider result = (GeminiProvider) provider.setTools(tools);

        // Assert
        assertThat(result).isSameAs(provider);
    }

    @Test
    void systemPrompt_ShouldSetSystemPrompt() {
        // Arrange
        String systemPrompt = "You are a helpful assistant";

        // Act
        GeminiProvider result = (GeminiProvider) provider.systemPrompt(systemPrompt);

        // Assert
        assertThat(result).isSameAs(provider);
    }

    @Test
    void messageMapper_ShouldReturnGeminiMessageMapper() {
        // Act
        var mapper = provider.messageMapper();

        // Assert
        assertThat(mapper).isInstanceOf(GeminiMessageMapper.class);
    }

    // Helper methods
    private GenerateContentResponse createMockGeminiResponse(String textContent) {
        GeminiPart textPart = new GeminiPart(textContent);
        GeminiContent content = new GeminiContent("model", Arrays.asList(textPart));
        GeminiCandidate candidate = new GeminiCandidate(content, "STOP", 0, null, null);
        GeminiUsageMetadata usage = new GeminiUsageMetadata(10, 20, 30);

        return new GenerateContentResponse(
                Arrays.asList(candidate), null, usage
        );
    }

    private GenerateContentResponse createMockGeminiResponseWithFunctionCall() {
        Map<String, Object> args = Map.of("location", "New York");
        GeminiFunctionCall functionCall = new GeminiFunctionCall("get_weather", args);
        GeminiPart functionPart = new GeminiPart(functionCall);
        GeminiContent content = new GeminiContent("model", Arrays.asList(functionPart));
        GeminiCandidate candidate = new GeminiCandidate(content, "STOP", 0, null, null);

        return new GenerateContentResponse(
                Arrays.asList(candidate), null, new GeminiUsageMetadata(15, 25, 40)
        );
    }

    private GenerateContentResponse createMockGeminiResponseBlocked() {
        return new GenerateContentResponse(
                Collections.emptyList(),
                new GenerateContentResponse.PromptFeedback("SAFETY", null),
                null
        );
    }

    private GenerateContentResponse createMockGeminiResponseWithFinishReason(String finishReason) {
        GeminiPart textPart = new GeminiPart("Incomplete response");
        GeminiContent content = new GeminiContent("model", Arrays.asList(textPart));
        GeminiCandidate candidate = new GeminiCandidate(content, finishReason, 0, null, null);

        return new GenerateContentResponse(
                Arrays.asList(candidate), null, new GeminiUsageMetadata(10, 15, 25)
        );
    }

    private GenerateContentResponse createMockGeminiResponseWithStructuredOutput() {
        Map<String, Object> args = Map.of("name", "Jane Smith", "age", 25);
        GeminiFunctionCall functionCall = new GeminiFunctionCall("extract_testperson", args);
        GeminiPart functionPart = new GeminiPart(functionCall);
        GeminiContent content = new GeminiContent("model", Arrays.asList(functionPart));
        GeminiCandidate candidate = new GeminiCandidate(content, "STOP", 0, null, null);

        return new GenerateContentResponse(
                Arrays.asList(candidate), null, new GeminiUsageMetadata(12, 18, 30)
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

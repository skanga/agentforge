package com.skanga.providers.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.chat.enums.MessageRole;
import com.skanga.chat.messages.Message;
import com.skanga.core.exceptions.ProviderException;
import com.skanga.core.messages.ToolCallMessage;
import com.skanga.providers.ollama.dto.OllamaChatMessage;
import com.skanga.providers.ollama.dto.OllamaChatResponse;
import com.skanga.providers.ollama.dto.OllamaToolCall;
import com.skanga.tools.BaseTool;
import com.skanga.tools.Tool;
import com.skanga.tools.ToolExecutionResult;
import com.skanga.tools.properties.PropertyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaProviderTests {

    @Mock
    private HttpClient mockHttpClient;
    @Mock
    private HttpResponse<String> mockStringHttpResponse;
    @Mock
    private HttpResponse<InputStream> mockInputStreamHttpResponse;

    private OllamaProvider ollamaProvider;
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "http://localhost:11434";
    private static final String MODEL_NAME = "llama2";

    @BeforeEach
    void setUp() {
        // Use a mutable map to allow tests to add parameters like "format":"json"
        ollamaProvider = new OllamaProvider(BASE_URL, MODEL_NAME, new HashMap<>());
        ollamaProvider.setHttpClient(mockHttpClient);
        objectMapper = new ObjectMapper();
    }

    // --- Constructor and Configuration Tests ---

    @Test
    void constructor_WithValidParameters_ShouldCreateInstance() {
        assertThat(ollamaProvider).isNotNull();
    }

    @Test
    void constructor_WithNullBaseUrl_ShouldThrowException() {
        assertThrows(NullPointerException.class, () ->
                new OllamaProvider(null, MODEL_NAME, Collections.emptyMap()));
    }

    @Test
    void constructor_WithNullModelName_ShouldThrowException() {
        assertThrows(NullPointerException.class, () ->
                new OllamaProvider(BASE_URL, null, Collections.emptyMap()));
    }

    @Test
    void constructor_ShouldNormalizeBaseUrl() {
        OllamaProvider provider1 = new OllamaProvider("http://localhost:11434/api", MODEL_NAME, Collections.emptyMap());
        OllamaProvider provider2 = new OllamaProvider("http://localhost:11434/", MODEL_NAME, Collections.emptyMap());
        OllamaProvider provider3 = new OllamaProvider("http://localhost:11434", MODEL_NAME, Collections.emptyMap());

        // This is hard to test without exposing internal state, but we can verify construction succeeds
        assertThat(provider1).isNotNull();
        assertThat(provider2).isNotNull();
        assertThat(provider3).isNotNull();
    }

    @Test
    void systemPrompt_ShouldSetSystemPrompt() {
        String systemPrompt = "You are a helpful assistant";
        OllamaProvider result = (OllamaProvider) ollamaProvider.systemPrompt(systemPrompt);
        assertThat(result).isSameAs(ollamaProvider);
    }

    @Test
    void setTools_WithValidTools_ShouldSetTools() {
        Tool tool = createTestTool("test_function", "Test function");
        OllamaProvider result = (OllamaProvider) ollamaProvider.setTools(List.of(tool));
        assertThat(result).isSameAs(ollamaProvider);
    }

    @Test
    void setHttpClient_ShouldSetClient() {
        HttpClient newClient = mock(HttpClient.class);
        OllamaProvider result = (OllamaProvider) ollamaProvider.setHttpClient(newClient);
        assertThat(result).isSameAs(ollamaProvider);
    }

    @Test
    void messageMapper_ShouldReturnOllamaMessageMapper() {
        var mapper = ollamaProvider.messageMapper();
        assertThat(mapper).isInstanceOf(OllamaMessageMapper.class);
    }

    // --- Chat Tests (Async and Sync) ---

    @Test
    void chatAsync_WithSimpleMessage_ShouldReturnResponse() throws Exception {
        List<Message> messages = List.of(new Message(MessageRole.USER, "Hello"));
        OllamaChatMessage responseMessageContent = new OllamaChatMessage("assistant", "Hello from Ollama Llama2!", null, null);
        OllamaChatResponse mockApiResponse = new OllamaChatResponse(MODEL_NAME, "timestamp", responseMessageContent, true, 100L, 50L, 10, 10L, 5, 5L);
        String responseJson = objectMapper.writeValueAsString(mockApiResponse);

        setupMockStringResponse(200, responseJson);

        Message result = ollamaProvider.chatAsync(messages, null, null).get();

        assertThat(result).isNotNull();
        assertThat(result.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(result.getContent()).isEqualTo("Hello from Ollama Llama2!");
        assertThat(result.getUsage()).isNotNull();
        assertThat(result.getUsage().promptTokens()).isEqualTo(10);
        assertThat(result.getUsage().completionTokens()).isEqualTo(5);
        assertThat(result.getUsage().totalTokens()).isEqualTo(15);
    }

    @Test
    void chatAsync_WithToolCalls_ShouldReturnToolCallMessage() throws Exception {
        List<Message> messages = List.of(new Message(MessageRole.USER, "What's the weather in Tokyo?"));
        Tool weatherTool = createTestTool("get_weather", "Gets the weather for a location.");

        List<OllamaToolCall> ollamaToolCalls = List.of(
                new OllamaToolCall(null, "function",
                        new OllamaToolCall.OllamaFunction("get_weather", Map.of("location", "Tokyo"))
                )
        );
        OllamaChatMessage responseMessageContent = new OllamaChatMessage("assistant", null, null, ollamaToolCalls);
        OllamaChatResponse mockApiResponse = new OllamaChatResponse(MODEL_NAME, "ts", responseMessageContent, true, 10L, 20L, 5, 5L, 10, 10L);
        String responseJson = objectMapper.writeValueAsString(mockApiResponse);

        setupMockStringResponse(200, responseJson);

        Message result = ollamaProvider.chatAsync(messages, null, List.of(weatherTool)).get();

        assertThat(result.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(result.getContent()).isInstanceOf(ToolCallMessage.class);

        ToolCallMessage tcm = (ToolCallMessage) result.getContent();
        assertThat(tcm.toolCalls()).hasSize(1);

        ToolCallMessage.ToolCall tc = tcm.toolCalls().get(0);
        assertThat(tc.id()).startsWith("ollama-tc-");
        assertThat(tc.function().name()).isEqualTo("get_weather");
        assertThat(tc.function().arguments()).isEqualTo("{\"location\":\"Tokyo\"}");
    }

    @Test
    void chat_ShouldCallChatAsync() throws Exception {
        List<Message> messages = List.of(new Message(MessageRole.USER, "Hello"));
        OllamaChatResponse mockResponse = createMockChatResponse("Hello back!");
        String responseJson = objectMapper.writeValueAsString(mockResponse);

        setupMockStringResponse(200, responseJson);

        Message result = ollamaProvider.chat(messages);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("Hello back!");
    }

    // --- Streaming Tests ---

    @Test
    void stream_ShouldReturnStreamOfChunks() throws Exception {
        List<Message> messages = List.of(new Message(MessageRole.USER, "Tell me a story"));

        OllamaChatResponse chunk1 = new OllamaChatResponse(MODEL_NAME, "ts1", new OllamaChatMessage("assistant", "Once", null, null), false, null, null, null, null, null, null);
        OllamaChatResponse chunk2 = new OllamaChatResponse(MODEL_NAME, "ts2", new OllamaChatMessage("assistant", " upon", null, null), false, null, null, null, null, null, null);
        OllamaChatResponse chunk3 = new OllamaChatResponse(MODEL_NAME, "ts3", new OllamaChatMessage("assistant", " a time", null, null), false, null, null, null, null, null, null);
        OllamaChatResponse finalChunk = new OllamaChatResponse(MODEL_NAME, "ts4", new OllamaChatMessage("assistant", "!", null, null), true, 100L, 50L, 15, 15L, 25, 25L);

        String mockStreamBody = objectMapper.writeValueAsString(chunk1) + "\n" +
                objectMapper.writeValueAsString(chunk2) + "\n" +
                objectMapper.writeValueAsString(chunk3) + "\n" +
                objectMapper.writeValueAsString(finalChunk) + "\n";

        setupMockInputStreamResponse(200, mockStreamBody);

        Stream<String> stream = ollamaProvider.stream(messages, null, Collections.emptyList());
        List<String> chunks = stream.collect(Collectors.toList());

        assertThat(chunks).containsExactly("Once", " upon", " a time", "!");
        // Verify usage was captured from the final stream chunk
        assertThat(ollamaProvider.streamPromptEvalCount.get()).isEqualTo(15);
        assertThat(ollamaProvider.streamEvalCount.get()).isEqualTo(25);
    }

    // --- Structured Output Tests ---

    @Test
    void structured_WithJsonFormat_ShouldReturnParsedObject() throws Exception {
        List<Message> messages = List.of(new Message(MessageRole.USER, "Generate a person object"));
        String expectedJsonContent = "{\"name\":\"OllamaStruct\",\"value\":789}";
        OllamaChatMessage responseMessageContent = new OllamaChatMessage("assistant", expectedJsonContent, null, null);
        OllamaChatResponse mockApiResponse = new OllamaChatResponse(MODEL_NAME, "ts-struct", responseMessageContent, true, 1L, 1L, 10, 1L, 10, 1L);
        String responseJson = objectMapper.writeValueAsString(mockApiResponse);
        setupMockStringResponse(200, responseJson);

        // Set "format":"json" in parameters for this specific test
        ollamaProvider.parameters.put("format", "json");

        TestStructuredResponse result = ollamaProvider.structured(messages, TestStructuredResponse.class, Map.of());

        assertThat(result).isNotNull();
        assertThat(result.name).isEqualTo("OllamaStruct");
        assertThat(result.value).isEqualTo(789);
    }


    // --- Error Handling Tests ---

    @Test
    void chatAsync_WithHttpError_ShouldThrowProviderException() throws IOException, InterruptedException {
        setupMockStringResponse(500, "Internal Server Error");
        CompletableFuture<Message> future = ollamaProvider.chatAsync(List.of(new Message(MessageRole.USER, "Hello")), null, null);

        ProviderException exception = assertThrows(ProviderException.class, future::get);
        assertThat(exception.getMessage()).contains("chat request failed with status 500");
    }

    @Test
    void chatAsync_WithMissingMessageField_ShouldThrowProviderException() throws IOException, InterruptedException {
        String invalidResponseJson = "{\"model\":\"llama2\",\"done\":true}"; // Missing 'message' field
        setupMockStringResponse(200, invalidResponseJson);
        CompletableFuture<Message> future = ollamaProvider.chatAsync(List.of(new Message(MessageRole.USER, "Hello")), null, null);

        ProviderException exception = assertThrows(ProviderException.class, future::get);
        assertThat(exception.getMessage()).contains("Response from Ollama is malformed: missing 'message' field");
    }

    @Test
    void stream_WithHttpError_ShouldThrowProviderException() throws Exception {
        when(mockInputStreamHttpResponse.statusCode()).thenReturn(404);
        when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofInputStream())))
                .thenReturn(mockInputStreamHttpResponse);

        ProviderException exception = assertThrows(ProviderException.class, () ->
                ollamaProvider.stream(List.of(new Message(MessageRole.USER, "Hello")), null, null));
        assertThat(exception.getMessage()).contains("stream request failed with status 404");
    }

    @Test
    void structured_WithInvalidJson_ShouldThrowProviderException() throws Exception {
        List<Message> messages = List.of(new Message(MessageRole.USER, "Generate invalid JSON"));
        OllamaChatResponse mockResponse = createMockChatResponse("{invalid json}");
        String responseJson = objectMapper.writeValueAsString(mockResponse);

        setupMockStringResponse(200, responseJson);

        ProviderException exception = assertThrows(ProviderException.class, () ->
                ollamaProvider.structured(messages, TestPerson.class, Map.of()));
        assertThat(exception.getMessage()).contains("was not valid JSON");
    }

    // --- Helper Methods ---

    private void setupMockStringResponse(int statusCode, String jsonBody) throws IOException, InterruptedException {
        when(mockStringHttpResponse.statusCode()).thenReturn(statusCode);
        when(mockStringHttpResponse.body()).thenReturn(jsonBody);
        when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockStringHttpResponse);
    }

    private void setupMockInputStreamResponse(int statusCode, String streamBody) throws IOException, InterruptedException {
        when(mockInputStreamHttpResponse.statusCode()).thenReturn(statusCode);
        InputStream streamInputStream = new ByteArrayInputStream(streamBody.getBytes(StandardCharsets.UTF_8));
        when(mockInputStreamHttpResponse.body()).thenReturn(streamInputStream);
        when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofInputStream())))
                .thenReturn(mockInputStreamHttpResponse);
    }

    private OllamaChatResponse createMockChatResponse(String content) {
        OllamaChatMessage message = new OllamaChatMessage("assistant", content, null, null);
        return new OllamaChatResponse(MODEL_NAME, null, message, true, 100L, 50L, 10, 10L, 5, 5L);
    }

    private Tool createTestTool(String name, String description) {
        BaseTool tool = new BaseTool(name, description);
        tool.addParameter("location", PropertyType.STRING, "The location", true);
        tool.setCallable(input -> new ToolExecutionResult("Tool executed"));
        return tool;
    }

    // --- Helper Test Data Classes ---

    static class TestPerson {
        public String name;
        public int age;
    }

    static class TestStructuredResponse {
        public String name;
        public int value;
    }
}

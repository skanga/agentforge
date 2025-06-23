package com.skanga.rag.embeddings;

import com.fasterxml.jackson.databind.ObjectMapper; // Added
import org.junit.jupiter.api.BeforeEach; // Added
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith; // Added
import org.mockito.Mock; // Added
import org.mockito.junit.jupiter.MockitoExtension; // Added

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any; // Added
import static org.mockito.ArgumentMatchers.eq; // Added
import static org.mockito.Mockito.when; // Added

import java.io.IOException; // Added
import java.net.http.HttpClient; // Added
import java.net.http.HttpRequest; // Added
import java.net.http.HttpResponse; // Added
import java.util.List;


@ExtendWith(MockitoExtension.class) // Added
class VoyageEmbeddingProviderTests {

    private final String testApiKey = "vy-testapikey";
    private final String testModel = "voyage-2";
    private VoyageEmbeddingProvider provider;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private HttpClient mockHttpClient;
    @Mock
    private HttpResponse<String> mockHttpResponse;

    @BeforeEach
    void setUp() {
        provider = new VoyageEmbeddingProvider(testApiKey, testModel, mockHttpClient);
    }

    @Test
    void constructor_validArgs_initializes() {
        // provider is initialized in setUp with mockHttpClient
        assertNotNull(provider);
    }

    @Test
    void constructor_nullApiKey_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new VoyageEmbeddingProvider(null, testModel, mockHttpClient));
    }

    @Test
    void constructor_nullModelName_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new VoyageEmbeddingProvider(testApiKey, null, mockHttpClient));
    }

    @Test
    void constructor_nullHttpClient_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new VoyageEmbeddingProvider(testApiKey, testModel, null));
    }


    @Test
    void embedText_emptyText_throwsEmbeddingException() {
        // VoyageEmbeddingProvider provider = new VoyageEmbeddingProvider(testApiKey, testModel); // Uses provider from setUp
        Exception exception = assertThrows(EmbeddingException.class, () -> {
            provider.embedText("");
        });
        assertTrue(exception.getMessage().contains("Text to embed cannot be empty"));

        Exception exception2 = assertThrows(EmbeddingException.class, () -> {
            provider.embedText("   ");
        });
        assertTrue(exception2.getMessage().contains("Text to embed cannot be empty"));
    }

    @Test
    void embedText_nullText_throwsNullPointerException() {
        // VoyageEmbeddingProvider provider = new VoyageEmbeddingProvider(testApiKey, testModel); // Uses provider from setUp
        assertThrows(NullPointerException.class, () -> provider.embedText(null));
    }

    @Test
    void embedText_successfulApiCall_returnsEmbedding() throws IOException, InterruptedException {
        String sampleText = "This is a test for Voyage embedder.";
        List<Double> expectedEmbedding = List.of(0.1, 0.2, 0.3);
        VoyageEmbeddingProvider.VoyageEmbeddingData embeddingData = new VoyageEmbeddingProvider.VoyageEmbeddingData("embedding", expectedEmbedding, 0);
        VoyageEmbeddingProvider.VoyageEmbeddingResponse mockApiResponse = new VoyageEmbeddingProvider.VoyageEmbeddingResponse("list", List.of(embeddingData), testModel, new VoyageEmbeddingProvider.VoyageUsage(5));
        String responseJson = objectMapper.writeValueAsString(mockApiResponse);

        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(responseJson);
        when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockHttpResponse);

        List<Double> result = provider.embedText(sampleText);
        assertNotNull(result);
        assertEquals(expectedEmbedding, result);
    }

    @Test
    void embedText_apiReturnsError_throwsEmbeddingException() throws IOException, InterruptedException {
        String sampleText = "This text will cause an API error.";
        String errorJson = "{\"detail\":\"Invalid API key\"}";

        when(mockHttpResponse.statusCode()).thenReturn(401);
        when(mockHttpResponse.body()).thenReturn(errorJson);
        when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockHttpResponse);

        EmbeddingException ex = assertThrows(EmbeddingException.class, () -> {
            provider.embedText(sampleText);
        });

        assertTrue(ex.getMessage().contains("Voyage AI API request for embedding failed"));
        assertEquals(401, ex.getStatusCode());
        assertEquals(errorJson, ex.getErrorBody());
    }

    // When VoyageEmbeddingProvider is fully implemented, add tests similar to OpenAI/Ollama:
    // - testEmbedText_successfulApiCall_returnsEmbeddingList()
    // - testEmbedText_apiReturnsError_throwsEmbeddingException()
    // - testEmbedText_requestPayloadIncludesCorrectFields() (if specific fields like input_type exist)
}

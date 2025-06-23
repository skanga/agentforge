package com.skanga.rag.embeddings;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.providers.HttpClientManager;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.net.http.HttpClient;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An {@link EmbeddingProvider} implementation for Voyage AI embedding models.
 * <b>Note: This is a skeleton implementation.</b> The actual API call logic in
 * {@link #embedText(String)} is not implemented and will return an empty list or
 * throw an {@link UnsupportedOperationException}.
 *
 * <p>To fully implement this provider, you would need to:
 * <ol>
 *   <li>Consult the Voyage AI API documentation for their embeddings endpoint
 *       (e.g., URL, request/response format, authentication).</li>
 *   <li>Create DTOs (Data Transfer Objects) for the Voyage AI request and response payloads
 *       if they are complex JSON structures, or use Maps.</li>
 *   <li>Implement the HTTP request logic in {@link #embedText(String)} using the
 *       JDK {@link HttpClient} to send the request and parse the response.</li>
 *   <li>Handle API errors and map them to {@link EmbeddingException}.</li>
 * </ol>
 * </p>
 *
 * <p>Example DTOs might include:
 * <ul>
 *   <li>`VoyageEmbeddingRequest(List<String> input, String model, String input_type, ...)`</li>
 *   <li>`VoyageEmbeddingResponse(List<VoyageEmbeddingData> data, String model, VoyageUsage usage)`</li>
 *   <li>`VoyageEmbeddingData(List<Float> embedding, int index)`</li>
 *   <li>`VoyageUsage(int total_tokens)`</li>
 * </ul>
 * </p>
 */
public class VoyageEmbeddingProvider extends AbstractEmbeddingProvider {

    private final String apiKey;
    private final String modelName; // e.g., "voyage-2", "voyage-code-2", "voyage-large-2"
    private final String baseUri;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /** Default Voyage AI API endpoint for embeddings. */
    public static final String DEFAULT_VOYAGE_API_BASE_URI = "https://api.voyageai.com/v1/embeddings";
    /** Example default model. User should specify one. */
    public static final String DEFAULT_VOYAGE_MODEL = "voyage-large-2";

    /**
     * Constructs a VoyageEmbeddingProvider.
     *
     * @param apiKey    Your Voyage AI API key. Must not be null.
     * @param modelName The Voyage AI embedding model to use (e.g., "voyage-large-2"). Must not be null.
     */
    /**
     * Primary constructor for VoyageEmbeddingProvider.
     * @param apiKey Your Voyage AI API key.
     * @param modelName The Voyage AI embedding model to use.
     * @param httpClient The HttpClient to use for requests.
     */
    public VoyageEmbeddingProvider(String apiKey, String modelName, HttpClient httpClient) {
        Objects.requireNonNull(apiKey, "Voyage AI API key cannot be null.");
        Objects.requireNonNull(modelName, "Voyage AI Model name cannot be null.");
        Objects.requireNonNull(httpClient, "HttpClient cannot be null.");

        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUri = DEFAULT_VOYAGE_API_BASE_URI;
        this.objectMapper = new ObjectMapper();
        this.httpClient = httpClient;
    }

    /**
     * Constructs a VoyageEmbeddingProvider using the default HttpClient.
     *
     * @param apiKey    Your Voyage AI API key. Must not be null.
     * @param modelName The Voyage AI embedding model to use (e.g., "voyage-large-2"). Must not be null.
     */
    public VoyageEmbeddingProvider(String apiKey, String modelName) {
        this(apiKey, modelName, HttpClientManager.getSharedClient());
    }

    /**
     * {@inheritDoc}
     * <p><b>This method is a placeholder and not fully implemented.</b>
     * It will print a warning to `System.err` and return an empty list.
     * To make this functional, the actual HTTP API call to Voyage AI's
     * embedding endpoint needs to be implemented, including request/response
     * handling and error management.</p>
     *
     * @throws EmbeddingException if text is null or empty.
     * @throws UnsupportedOperationException if called (can be uncommented for stricter behavior).
     */
    @Override
    public List<Double> embedText(String text) throws EmbeddingException {
        Objects.requireNonNull(text, "Text to embed cannot be null.");
        if (text.trim().isEmpty()) {
            throw new EmbeddingException("Text to embed cannot be empty or whitespace only.");
        }

        VoyageEmbeddingRequest requestPayload = new VoyageEmbeddingRequest(this.modelName, text);

        String requestBodyJson;
        try {
            requestBodyJson = objectMapper.writeValueAsString(requestPayload);
        } catch (Exception e) {
            throw new EmbeddingException("Failed to serialize Voyage embedding request to JSON", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.baseUri))
                .header("Authorization", "Bearer " + this.apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .timeout(Duration.ofSeconds(30))
                .build();

        try {
            HttpResponse<String> httpResponse = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != 200) {
                throw new EmbeddingException("Voyage AI API request for embedding failed",
                    httpResponse.statusCode(), httpResponse.body());
            }

            String responseBody = httpResponse.body();
            VoyageEmbeddingResponse embeddingResponse = objectMapper.readValue(responseBody, VoyageEmbeddingResponse.class);

            if (embeddingResponse == null || embeddingResponse.data() == null || embeddingResponse.data().isEmpty()) {
                throw new EmbeddingException("Voyage AI embedding response is empty or missing 'data'. Body: " + responseBody);
            }

            if (embeddingResponse.data().get(0).embedding() == null) {
                throw new EmbeddingException("Voyage AI embedding data is missing the 'embedding' vector. Body: " + responseBody);
            }

            return embeddingResponse.data().get(0).embedding();

        } catch (java.io.IOException e) { // Includes JsonProcessingException
            // This catch is for when statusCode WAS 200, but the body was unparseable.
            throw new EmbeddingException("Failed to deserialize Voyage AI embedding response from JSON: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("Voyage AI API call was interrupted: " + e.getMessage(), e);
        } catch (Exception e) {
            if (e instanceof EmbeddingException) { // If it's already an EmbeddingException (e.g. from status code check)
                throw (EmbeddingException) e;    // Re-throw it to preserve status code and original body.
            }
            // For other unexpected errors during the process
            throw new EmbeddingException("Error during Voyage AI API call for embedding: " + e.getMessage(), e);
        }
    }

    // Request DTO
    // Making DTOs public static for testability and general good practice for DTOs if used externally.
    public static record VoyageEmbeddingRequest(
            @JsonProperty("model") String model,
            @JsonProperty("input") String input,
            @JsonProperty("input_type") String inputType
    ) {
        public VoyageEmbeddingRequest(String model, String input) {
            this(model, input, "document"); // Default input type
        }
    }

    // Response DTOs
    public static record VoyageEmbeddingResponse(
            @JsonProperty("object") String object,
            @JsonProperty("data") List<VoyageEmbeddingData> data,
            @JsonProperty("model") String model,
            @JsonProperty("usage") VoyageUsage usage
    ) {}

    public static record VoyageEmbeddingData(
            @JsonProperty("object") String object,
            @JsonProperty("embedding") List<Double> embedding,
            @JsonProperty("index") int index
    ) {}

    public static record VoyageUsage(
            @JsonProperty("total_tokens") int totalTokens
    ) {}
}
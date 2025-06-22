package com.skanga.rag.embeddings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.providers.HttpClientManager;
import com.skanga.providers.openai.dto.OpenAIEmbeddingRequest;
import com.skanga.providers.openai.dto.OpenAIEmbeddingResponse;
// OpenAIEmbeddingData is not directly used as we access embedding via response.data().get(0).embedding()

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * An {@link EmbeddingProvider} implementation that uses OpenAI's API to generate embeddings.
 * It utilizes the JDK's built-in {@link HttpClient} for making HTTP requests.
 *
 * <p>This provider requires an OpenAI API key and a model name (e.g., "text-embedding-ada-002",
 * "text-embedding-3-small", "text-embedding-3-large"). It supports specifying optional
 * dimensions for newer embedding models.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * EmbeddingProvider embedder = new OpenAIEmbeddingProvider("YOUR_API_KEY", "text-embedding-3-small", 256);
 * List<Float> embedding = embedder.embedText("Hello, world!");
 * Document doc = new Document("Some text");
 * embedder.embedDocument(doc); // doc.getEmbedding() will be populated
 * }</pre>
 *
 * <p>Note: This implementation uses the JDK 11+ HttpClient.
 * The main providers (like OpenAIProvider for chat) currently use Apache HttpClient 5.
 * This could be standardized in the future if desired.</p>
 */
public class OpenAIEmbeddingProvider extends AbstractEmbeddingProvider {

    private final String apiKey;
    private final String modelName;
    private final Integer dimensions; // Optional, for newer models like text-embedding-3-small/large
    private final String baseUri;
    private final ObjectMapper objectMapper;

    /** Default OpenAI API endpoint for embeddings. */
    public static final String DEFAULT_OPENAI_EMBEDDINGS_API_BASE_URI = "https://api.openai.com/v1/embeddings";
    /** Default model if none specified, though specifying is highly recommended. */
    public static final String DEFAULT_OPENAI_EMBEDDING_MODEL = "text-embedding-ada-002";


    /**
     * Constructs an OpenAIEmbeddingProvider with specified API key, model name, and optional dimensions.
     *
     * @param apiKey     Your OpenAI API key. Must not be null.
     * @param modelName  The OpenAI embedding model to use (e.g., "text-embedding-3-small"). Must not be null.
     * @param dimensions Optional: The desired number of dimensions for the output embedding.
     *                   Supported by newer models like "text-embedding-3-small" and "text-embedding-3-large".
     *                   Can be null if not needed or using older models.
     */
    public OpenAIEmbeddingProvider(String apiKey, String modelName, Integer dimensions) {
        Objects.requireNonNull(apiKey, "OpenAI API key cannot be null.");
        Objects.requireNonNull(modelName, "OpenAI model name cannot be null.");

        this.apiKey = apiKey;
        this.modelName = modelName;
        this.dimensions = dimensions;
        this.baseUri = DEFAULT_OPENAI_EMBEDDINGS_API_BASE_URI;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Constructs an OpenAIEmbeddingProvider with specified API key and model name, and no specific dimensions.
     *
     * @param apiKey    Your OpenAI API key.
     * @param modelName The OpenAI embedding model to use.
     */
    public OpenAIEmbeddingProvider(String apiKey, String modelName) {
        this(apiKey, modelName, null);
    }

    /**
     * Constructs an OpenAIEmbeddingProvider with specified API key, using the default embedding model.
     * It is recommended to specify a model explicitly.
     * @param apiKey Your OpenAI API key.
     * @deprecated Use constructors that specify the model name.
     */
    @Deprecated
    public OpenAIEmbeddingProvider(String apiKey) {
        this(apiKey, DEFAULT_OPENAI_EMBEDDING_MODEL, null);
    }


    /**
     * {@inheritDoc}
     * <p>This implementation calls the OpenAI embeddings API.</p>
     *
     * @throws EmbeddingException if text is null/empty, or if API call or JSON processing fails.
     */
    @Override
    public List<Double> embedText(String text) throws EmbeddingException {
        Objects.requireNonNull(text, "Text to embed cannot be null.");
        // OpenAI API v1/embeddings endpoint expects non-empty input.
        // While the API might support multiple inputs, this method processes one string.
        // Batching is handled by embedDocuments -> embedDocument -> embedText loop,
        // or could be overridden in embedDocuments for providers that support batch input well.
        if (text.trim().isEmpty()) {
            // Or return a zero vector of appropriate dimension if known, but failing is safer.
            throw new EmbeddingException("Text to embed cannot be empty or whitespace only.");
        }

        OpenAIEmbeddingRequest requestPayload = new OpenAIEmbeddingRequest(this.modelName, text, this.dimensions);

        String requestBodyJson;
        try {
            requestBodyJson = objectMapper.writeValueAsString(requestPayload);
        } catch (JsonProcessingException e) {
            throw new EmbeddingException("Failed to serialize OpenAI embedding request to JSON", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.baseUri))
                .header("Authorization", "Bearer " + this.apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();

        try {
            HttpResponse<String> httpResponse = HttpClientManager.getSharedClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != 200) {
                throw new EmbeddingException("OpenAI API request for embedding failed", httpResponse.statusCode(), httpResponse.body());
            }

            String responseBody = httpResponse.body();
            OpenAIEmbeddingResponse embeddingResponse = objectMapper.readValue(responseBody, OpenAIEmbeddingResponse.class);

            if (embeddingResponse == null || embeddingResponse.data() == null || embeddingResponse.data().isEmpty()) {
                throw new EmbeddingException("OpenAI embedding response is empty or missing 'data'. Body: " + responseBody);
            }
            // For a single input text, OpenAI returns a list containing one embedding data object.
            if (embeddingResponse.data().get(0).embedding() == null) {
                 throw new EmbeddingException("OpenAI embedding data is missing the 'embedding' vector. Body: " + responseBody);
            }

            return embeddingResponse.data().get(0).embedding();

        } catch (JsonProcessingException e) {
            throw new EmbeddingException("Failed to deserialize OpenAI embedding response from JSON: " + e.getMessage(), e);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // Preserve interrupt status
            }
            throw new EmbeddingException("Error during OpenAI API call for embedding: " + e.getMessage(), e);
        }
    }
}

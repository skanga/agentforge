package com.skanga.rag.embeddings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.providers.HttpClientManager;
import com.skanga.providers.ollama.dto.OllamaEmbeddingRequest;
import com.skanga.providers.ollama.dto.OllamaEmbeddingResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * An {@link EmbeddingProvider} implementation that uses a locally running Ollama instance
 * to generate embeddings via its `/api/embeddings` endpoint.
 * It utilizes the JDK's built-in {@link HttpClient} for making HTTP requests.
 *
 * <p>This provider requires the base URL of the Ollama server (e.g., "http://localhost:11434")
 * and the name of the embedding model deployed in Ollama (e.g., "nomic-embed-text", "mxbai-embed-large").</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * EmbeddingProvider embedder = new OllamaEmbeddingProvider("http://localhost:11434", "nomic-embed-text");
 * List<Float> embedding = embedder.embedText("Hello, Ollama!");
 * }</pre>
 *
 * <p>Note: This implementation uses the JDK 11+ HttpClient.</p>
 */
public class OllamaEmbeddingProvider extends AbstractEmbeddingProvider {

    private final String baseUrl; // e.g., "http://localhost:11434" (without /api)
    private final String modelName;
    private final ObjectMapper objectMapper;
    private final Map<String, Object> options; // Optional parameters for Ollama embeddings request

    /** Default Ollama API endpoint for embeddings relative to base URL. */
    public static final String DEFAULT_OLLAMA_EMBEDDINGS_API_PATH = "/api/embeddings";

    /**
     * Constructs an OllamaEmbeddingProvider.
     *
     * @param baseUrl    The base URL of the Ollama server (e.g., "http://localhost:11434"). Must not be null.
     * @param modelName  The name of the embedding model hosted by Ollama. Must not be null.
     * @param options    Optional map of parameters to pass to the Ollama API (e.g., "num_ctx", "temperature"). Can be null.
     */
    public OllamaEmbeddingProvider(String baseUrl, String modelName, Map<String, Object> options) {
        Objects.requireNonNull(baseUrl, "Ollama base URL cannot be null.");
        Objects.requireNonNull(modelName, "Ollama model name cannot be null.");

        // Normalize baseUrl to ensure it doesn't end with common API paths
        String normalizedBaseUrl = baseUrl;
        if (normalizedBaseUrl.endsWith("/api")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - "/api".length());
        }
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        this.baseUrl = normalizedBaseUrl;
        this.modelName = modelName;
        this.options = (options == null) ? Collections.emptyMap() : new HashMap<>(options);

        this.objectMapper = new ObjectMapper();
    }

    /**
     * Constructs an OllamaEmbeddingProvider without additional model options.
     *
     * @param baseUrl   The base URL of the Ollama server.
     * @param modelName The name of the embedding model.
     */
    public OllamaEmbeddingProvider(String baseUrl, String modelName) {
        this(baseUrl, modelName, null);
    }

    /**
     * {@inheritDoc}
     * <p>This implementation calls the Ollama `/api/embeddings` endpoint.</p>
     *
     * @throws EmbeddingException if text is null/empty, or if API call or JSON processing fails.
     */
    @Override
    public List<Double> embedText(String text) throws EmbeddingException {
        Objects.requireNonNull(text, "Text to embed cannot be null.");
        if (text.trim().isEmpty()) {
            throw new EmbeddingException("Text to embed cannot be empty or whitespace only.");
        }

        // Ollama uses "prompt" for the input text in the /api/embeddings endpoint
        OllamaEmbeddingRequest requestPayload = new OllamaEmbeddingRequest(this.modelName, text, this.options.isEmpty() ? null : this.options);

        String requestBodyJson;
        try {
            requestBodyJson = objectMapper.writeValueAsString(requestPayload);
        } catch (JsonProcessingException e) {
            throw new EmbeddingException("Failed to serialize Ollama embedding request to JSON", e);
        }

        String requestUrl = this.baseUrl + DEFAULT_OLLAMA_EMBEDDINGS_API_PATH;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();

        try {
            HttpResponse<String> httpResponse = HttpClientManager.getSharedClient().send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = httpResponse.body();

            if (httpResponse.statusCode() != 200) {
                String errorMessage = "Ollama API request for embedding failed";
                if (responseBody != null && !responseBody.isEmpty()) {
                    try {
                        // Ollama errors are often simple JSON like {"error": "message"}
                        Map<String, String> errorMap = objectMapper.readValue(responseBody, new TypeReference<>() {
                        });
                        if (errorMap.containsKey("error")) {
                            errorMessage += ": " + errorMap.get("error");
                        }
                    } catch (JsonProcessingException e) {
                        // Error body wasn't JSON or didn't match expected structure, use raw body in exception
                    }
                }
                throw new EmbeddingException(errorMessage, httpResponse.statusCode(), responseBody);
            }

            OllamaEmbeddingResponse embeddingResponse = objectMapper.readValue(responseBody, OllamaEmbeddingResponse.class);

            if (embeddingResponse == null || embeddingResponse.embedding() == null) {
                throw new EmbeddingException("Ollama embedding response is empty or missing 'embedding' data. Body: " + responseBody);
            }

            return embeddingResponse.embedding();

        } catch (JsonProcessingException e) { // Catch error from parsing valid (200) response
            throw new EmbeddingException("Failed to deserialize Ollama embedding response from JSON: " + e.getMessage() + ". Body: " + (e.getLocation() != null ? e.getLocation().contentReference().getRawContent() : "N/A"), e);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // Preserve interrupt status
            }
            throw new EmbeddingException("Error during Ollama API call for embedding: " + e.getMessage(), e);
        }
    }
}

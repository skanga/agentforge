package com.skanga.rag.vectorstore.chroma;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.providers.HttpClientManager;
import com.skanga.rag.Document;
import com.skanga.rag.vectorstore.VectorStore;
import com.skanga.rag.vectorstore.VectorStoreException;
import com.skanga.rag.vectorstore.chroma.dto.ChromaQueryRequest;
import com.skanga.rag.vectorstore.chroma.dto.ChromaQueryResponse;
import com.skanga.rag.vectorstore.chroma.dto.ChromaUpsertRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * A {@link VectorStore} implementation for interacting with a ChromaDB instance.
 * This client uses the JDK's built-in {@link HttpClient} to communicate with ChromaDB's REST API.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Adds documents (with pre-computed embeddings) to a specified ChromaDB collection using the `/upsert` endpoint.</li>
 *   <li>Performs similarity searches using the `/query` endpoint.</li>
 *   <li>Maps results from ChromaDB back to {@link com.skanga.rag.Document} objects.</li>
 * </ul>
 * </p>
 *
 * <p><b>ChromaDB API Interaction:</b>
 * This implementation assumes a running ChromaDB server accessible via HTTP.
 * It uses DTOs (Data Transfer Objects) in the {@code com.skanga.rag.vectorstore.chroma.dto}
 * package for serializing requests and deserializing responses.
 * </p>
 *
 * <p><b>Error Handling:</b>
 * HTTP errors or issues with JSON processing will result in a {@link VectorStoreException}.</p>
 *
 * <p><b>Note on Embeddings:</b> This client expects that the {@link Document} objects
 * passed to {@code addDocuments} already have their vector embeddings populated.
 * Query embeddings must also be pre-computed.</p>
 */
public class ChromaVectorStore implements VectorStore {

    private final String collectionName;
    /** Base URL of the ChromaDB server, e.g., "http://localhost:8000". */
    private final String hostUrl;
    private final int defaultTopK;
    private final ObjectMapper objectMapper;

    /** Default value for K (top results) if not specified in constructor. */
    private static final int DEFAULT_K_CHROMA = 5;
    /** Default fields to include in ChromaDB query responses. */
    private static final List<String> DEFAULT_QUERY_INCLUDE = List.of("documents", "distances", "metadatas", "embeddings");


    /**
     * Constructs a ChromaVectorStore.
     *
     * @param hostUrl        The base URL of the ChromaDB server (e.g., "http://localhost:8000"). Must not be null.
     * @param collectionName The name of the collection to use in ChromaDB. Must not be null.
     * @param defaultTopK    A default value for 'k' (top results) if certain methods were to use it. Must be positive.
     * @throws IllegalArgumentException if defaultTopK is not positive.
     */
    public ChromaVectorStore(String hostUrl, String collectionName, int defaultTopK) {
        Objects.requireNonNull(hostUrl, "ChromaDB host URL cannot be null.");
        Objects.requireNonNull(collectionName, "ChromaDB collection name cannot be null.");
        if (defaultTopK <= 0) {
            throw new IllegalArgumentException("defaultTopK must be positive.");
        }

        // Normalize hostUrl to ensure no trailing slash, as API paths start with slash.
        this.hostUrl = hostUrl.endsWith("/") ? hostUrl.substring(0, hostUrl.length() - 1) : hostUrl;
        this.collectionName = collectionName;
        this.defaultTopK = defaultTopK;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Constructs a ChromaVectorStore with a default top-K value.
     * @param hostUrl        The base URL of the ChromaDB server.
     * @param collectionName The name of the collection.
     */
    public ChromaVectorStore(String hostUrl, String collectionName) {
        this(hostUrl, collectionName, DEFAULT_K_CHROMA);
    }

    /**
     * Helper to build URIs for ChromaDB API endpoints.
     * @param path The specific API path (e.g., "/collections/my_collection/upsert").
     * @return The fully formed URI.
     */
    private URI buildUri(String path) {
        // Chroma API v1 path structure
        return URI.create(this.hostUrl + "/api/v1" + path);
    }

    /**
     * {@inheritDoc}
     * <p>Delegates to {@link #addDocuments(List)}.</p>
     */
    @Override
    public void addDocument(Document document) throws VectorStoreException {
        addDocuments(Collections.singletonList(document));
    }

    /**
     * {@inheritDoc}
     * <p>Upserts documents to the configured ChromaDB collection.
     * All documents must have their embeddings pre-populated.</p>
     * @throws VectorStoreException if documents list is null, any document is null, a document
     *                              lacks an embedding, or if the API call fails.
     */
    @Override
    public void addDocuments(List<Document> documents) throws VectorStoreException {
        Objects.requireNonNull(documents, "Documents list cannot be null for ChromaDB upsert.");
        if (documents.isEmpty()) {
            return;
        }

        // Validate documents first
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            Objects.requireNonNull(doc, "Document at index " + i + " cannot be null for ChromaDB upsert.");
            if (doc.getEmbedding() == null || doc.getEmbedding().isEmpty()) {
                throw new VectorStoreException("Document at index " + i + " (ID: " + doc.getId() + ") has null or empty embedding");
            }
        }

        List<String> ids = new ArrayList<>(documents.size());
        List<List<Double>> embeddings = new ArrayList<>(documents.size());
        List<Map<String, Object>> metadatas = new ArrayList<>(documents.size());
        List<String> contents = new ArrayList<>(documents.size());

        for (Document doc : documents) {
            ids.add(doc.getId());
            embeddings.add(doc.getEmbedding());
            // Ensure metadata is not null for ChromaDB, use empty map if original is null
            metadatas.add(doc.getMetadata() != null ? new HashMap<>(doc.getMetadata()) : Collections.emptyMap());
            contents.add(doc.getContent());
        }

        ChromaUpsertRequest upsertRequest = new ChromaUpsertRequest(ids, embeddings, metadatas, contents);
        String requestBodyJson;
        try {
            requestBodyJson = objectMapper.writeValueAsString(upsertRequest);
        } catch (JsonProcessingException e) {
            throw new VectorStoreException("Failed to serialize Chroma upsert request to JSON", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(buildUri("/collections/" + this.collectionName + "/upsert"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();

        try {
            HttpResponse<String> httpResponse = HttpClientManager.getSharedClient().send(request, HttpResponse.BodyHandlers.ofString());
            // ChromaDB's /upsert usually returns 201 Created on success with new items,
            // or 200 OK if items were updated or already existed (behavior can vary slightly).
            // For simplicity, checking for 200 or 201.
            if (httpResponse.statusCode() != 200 && httpResponse.statusCode() != 201) {
                throw new VectorStoreException("ChromaDB upsert request failed", httpResponse.statusCode(), httpResponse.body());
            }
            // Success response body from /upsert is often minimal or just status, not typically parsed here.
        } catch (IOException e) {
            throw new VectorStoreException("I/O error during ChromaDB upsert API call: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Preserve interrupt status
            throw new VectorStoreException("ChromaDB upsert API call was interrupted", e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>Queries the ChromaDB collection for documents similar to the given embedding.
     * It requests documents, distances, metadatas, and embeddings to be included in the results.</p>
     * @throws IllegalArgumentException if k is not positive.
     * @throws VectorStoreException if queryEmbedding is null, or if the API call or response parsing fails.
     */
    @Override
    public List<Document> similaritySearch(List<Double> queryEmbedding, int k) throws VectorStoreException {
        Objects.requireNonNull(queryEmbedding, "Query embedding cannot be null for ChromaDB search.");
        if (k <= 0) {
            throw new IllegalArgumentException("Number of results to return (k) must be positive.");
        }
        if (queryEmbedding.isEmpty()) {
            throw new VectorStoreException("Query embedding cannot be empty for ChromaDB search.");
        }

        ChromaQueryRequest queryRequest = new ChromaQueryRequest(
                Collections.singletonList(queryEmbedding), // Chroma API expects a list of query embeddings
                k,
                DEFAULT_QUERY_INCLUDE // Request documents, distances, metadatas, embeddings
        );

        String requestBodyJson;
        try {
            requestBodyJson = objectMapper.writeValueAsString(queryRequest);
        } catch (JsonProcessingException e) {
            throw new VectorStoreException("Failed to serialize Chroma query request to JSON", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(buildUri("/collections/" + this.collectionName + "/query"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();

        try {
            HttpResponse<String> httpResponse = HttpClientManager.getSharedClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != 200) {
                throw new VectorStoreException("ChromaDB query request failed", httpResponse.statusCode(), httpResponse.body());
            }

            String responseBody = httpResponse.body();
            if (responseBody == null || responseBody.trim().isEmpty()) {
                throw new VectorStoreException("ChromaDB returned empty response body");
            }

            ChromaQueryResponse chromaResponse;
            try {
                chromaResponse = objectMapper.readValue(responseBody, ChromaQueryResponse.class);
            } catch (JsonProcessingException e) {
                throw new VectorStoreException("Failed to deserialize Chroma query response from JSON", e);
            }

            return processChromaQueryResponse(chromaResponse);

        } catch (JsonProcessingException e) { // Error deserializing Chroma's response
            throw new VectorStoreException("Failed to deserialize Chroma query response from JSON: " + e.getMessage(), e);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // Preserve interrupt status
            }
            throw new VectorStoreException("Error during ChromaDB query API call: " + e.getMessage(), e);
        }
    }

    /**
     * Processes the raw response from ChromaDB's query endpoint and converts it
     * into a list of {@link Document} objects.
     *
     * @param chromaResponse The parsed response from ChromaDB.
     * @return A list of {@link Document} objects.
     */
    private List<Document> processChromaQueryResponse(ChromaQueryResponse chromaResponse) {
        if (chromaResponse == null) return Collections.emptyList();

        // Assuming a single query was made, so we take the first list from each response field.
        List<String> ids = chromaResponse.getIdsForFirstQuery();
        List<String> contents = chromaResponse.getDocumentsForFirstQuery();
        List<Map<String, Object>> metadatas = chromaResponse.getMetadatasForFirstQuery();
        List<Double> distances = chromaResponse.getDistancesForFirstQuery();
        List<List<Double>> embeddings = chromaResponse.getEmbeddingsForFirstQuery();

        // Basic check: if ids list is null or empty, there are no results.
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        int numResults = ids.size();
        List<Document> resultDocuments = new ArrayList<>(numResults);

        for (int i = 0; i < numResults; i++) {
            String content = (contents != null && contents.size() > i) ? contents.get(i) : null;
            // If content is crucial and might be missing, decide on behavior (e.g., skip, use placeholder)
            if (content == null) {
                 System.err.println("Warning: ChromaDB result for ID " + ids.get(i) + " has null content. Skipping.");
                continue;
            }

            Document doc = new Document(content);
            doc.setId(ids.get(i)); // Use ID from ChromaDB

            if (metadatas != null && metadatas.size() > i) {
                doc.setMetadata(metadatas.get(i));
            }
            if (distances != null && distances.size() > i && distances.get(i) != null) {
                // Chroma's distance (e.g., L2, cosine) - smaller is better.
                // Convert to a similarity score where higher is better (e.g., 1.0 - distance for cosine).
                // For L2, score might be 1 / (1 + distance). For simplicity, using 1 - distance.
                doc.setScore((float) (1.0 - distances.get(i)));
            }
            if (embeddings != null && embeddings.size() > i && embeddings.get(i) != null) {
                doc.setEmbedding(embeddings.get(i));
            }

            resultDocuments.add(doc);
        }
        // ChromaDB's query endpoint returns results sorted by similarity/distance.
        return resultDocuments;
    }

    // Note: Methods for managing ChromaDB collections (create, delete, list, get)
    // could be added here if needed, interacting with endpoints like:
    // - POST /api/v1/collections
    // - DELETE /api/v1/collections/{collection_name}
    // - GET /api/v1/collections
    // - GET /api/v1/collections/{collection_name}
    // These are not part of the VectorStore interface currently but are common admin tasks.
}

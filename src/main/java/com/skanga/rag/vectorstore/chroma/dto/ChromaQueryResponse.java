package com.skanga.rag.vectorstore.chroma.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object (DTO) for ChromaDB query responses.
 * Represents the structure of the JSON response from the `/api/v1/collections/{collectionName}/query` endpoint.
 *
 * <p>ChromaDB returns results for each query embedding provided in the request.
 * Therefore, most fields are lists of lists (e.g., {@code List<List<String>> ids}).
 * The outer list corresponds to each query embedding sent (usually one in typical client usage).
 * The inner list contains the actual results (IDs, documents, distances, etc.) for that query.
 * </p>
 *
 * <p>This DTO includes helper methods like {@link #getIdsForFirstQuery()} for convenient
 * access when only a single query embedding was sent.</p>
 *
 * <p>The fields included in the response depend on the {@code "include"} parameter
 * sent in the {@link ChromaQueryRequest}. Fields not included will be null.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true) // Ignores any fields not defined here (e.g. "error" field from Chroma on failure)
@JsonInclude(JsonInclude.Include.NON_NULL)  // Don't serialize null fields if this DTO were ever serialized
public record ChromaQueryResponse(
    /** List of lists of document IDs. Outer list for queries, inner for results per query. */
    List<List<String>> ids,

    /** List of lists of embedding vectors. Null if not included in the request. */
    List<List<List<Double>>> embeddings,

    /** List of lists of document contents. Null if not included. */
    List<List<String>> documents,

    /** List of lists of metadata maps. Null if not included. */
    @JsonProperty("metadatas") List<List<Map<String, Object>>> metadatas,

    /** List of lists of distances. Null if not included. Smaller values usually mean more similar. */
    List<List<Double>> distances
) {
    // Note: The actual top-level response from ChromaDB can sometimes be a single JSON object
    // with these fields, or sometimes an array of such objects if the API implies multiple results
    // for a single query in a different way. This DTO assumes the common structure where fields
    // are lists of lists.

    /**
     * Helper to get the IDs for the results of the first query embedding.
     * Assumes only one query embedding was sent in the request.
     * @return List of document IDs for the first query, or null if no ID data.
     */
    public List<String> getIdsForFirstQuery() {
        return (ids != null && !ids.isEmpty()) ? ids.get(0) : Collections.emptyList();
    }

    /**
     * Helper to get the embeddings for the results of the first query embedding.
     * @return List of embedding vectors for the first query, or null if no embedding data.
     */
    public List<List<Double>> getEmbeddingsForFirstQuery() {
        return (embeddings != null && !embeddings.isEmpty()) ? embeddings.get(0) : Collections.emptyList();
    }

    /**
     * Helper to get the document contents for the results of the first query embedding.
     * @return List of document content strings for the first query, or null if no document data.
     */
    public List<String> getDocumentsForFirstQuery() {
        return (documents != null && !documents.isEmpty()) ? documents.get(0) : Collections.emptyList();
    }

    /**
     * Helper to get the metadata maps for the results of the first query embedding.
     * @return List of metadata maps for the first query, or null if no metadata.
     */
    public List<Map<String, Object>> getMetadatasForFirstQuery() {
        return (metadatas != null && !metadatas.isEmpty()) ? metadatas.get(0) : Collections.emptyList();
    }

    /**
     * Helper to get the distances for the results of the first query embedding.
     * @return List of distances for the first query, or null if no distance data.
     */
    public List<Double> getDistancesForFirstQuery() {
        return (distances != null && !distances.isEmpty()) ? distances.get(0) : Collections.emptyList();
    }
}

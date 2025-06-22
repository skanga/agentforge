package com.skanga.rag.vectorstore.chroma.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Data Transfer Object (DTO) for ChromaDB query requests.
 * Represents the payload sent to the `/api/v1/collections/{collectionName}/query` endpoint.
 *
 * @param queryEmbeddings List of query embedding vectors. Typically contains one vector for a single query.
 *                        Each vector is a {@code List<Float>}.
 * @param nResults        The number of most similar results to return for each query embedding.
 * @param include         A list of fields to include in the results (e.g., "documents", "distances",
 *                        "metadatas", "embeddings").
 * @param where           Optional: A map defining metadata filters to apply before the similarity search.
 *                        The structure of this map should conform to ChromaDB's filter syntax
 *                        (e.g., `{"source": "email"}`).
 * @param whereDocument   Optional: A map defining document content filters to apply.
 *                        Uses operators like `{$contains: "keyword"}`.
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // Exclude null fields (like where, whereDocument if not used)
public record ChromaQueryRequest(
    @JsonProperty("query_embeddings") List<List<Double>> queryEmbeddings,
    @JsonProperty("n_results") int nResults,
    List<String> include,
    @JsonProperty("where") Map<String, Object> where,
    @JsonProperty("where_document") Map<String, Object> whereDocument
) {
    /**
     * Canonical constructor for ChromaQueryRequest.
     */
    public ChromaQueryRequest {
        Objects.requireNonNull(queryEmbeddings, "queryEmbeddings list cannot be null.");
        if (queryEmbeddings.isEmpty()) {
            throw new IllegalArgumentException("queryEmbeddings list cannot be empty.");
        }
        if (nResults <= 0) {
            throw new IllegalArgumentException("nResults must be positive.");
        }
        Objects.requireNonNull(include, "include list cannot be null (can be empty).");
    }

    /**
     * Convenience constructor for basic queries without metadata or document content filters.
     *
     * @param queryEmbeddings List of query embedding vectors.
     * @param nResults        Number of results to return.
     * @param include         List of fields to include in the results.
     */
    public ChromaQueryRequest(List<List<Double>> queryEmbeddings, int nResults, List<String> include) {
        this(queryEmbeddings, nResults, include, null, null);
    }
}

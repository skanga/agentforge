package com.skanga.rag.vectorstore.chroma.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Data Transfer Object (DTO) for ChromaDB upsert requests.
 * Represents the payload sent to the `/api/v1/collections/{collectionName}/upsert` endpoint.
 * All lists (ids, embeddings, metadatas, documents) must have the same number of elements.
 *
 * @param ids List of unique identifiers for each document.
 * @param embeddings List of embedding vectors (each vector is a {@code List<Float>}).
 * @param metadatas List of metadata maps associated with each document.
 * @param documents List of document content strings.
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // Exclude null fields from JSON
public record ChromaUpsertRequest(
    List<String> ids,
    List<List<Double>> embeddings,
    @JsonProperty("metadatas") List<Map<String, Object>> metadatas,
    List<String> documents
) {
    /**
     * Canonical constructor for ChromaUpsertRequest.
     * Performs validation to ensure all lists are non-null and have consistent sizes if not empty.
     */
    public ChromaUpsertRequest {
        Objects.requireNonNull(ids, "ids list cannot be null for ChromaUpsertRequest.");
        Objects.requireNonNull(embeddings, "embeddings list cannot be null for ChromaUpsertRequest.");
        // Metadatas and documents can be null/empty if ChromaDB configuration allows it for an upsert,
        // but typically they are provided. For this client, let's assume they are often present.
        // For robustness, allow them to be null but ensure consistency if provided.

        int size = ids.size();
        if (embeddings.size() != size) {
            throw new IllegalArgumentException("embeddings list size must match ids list size.");
        }
        if (metadatas != null && metadatas.size() != size) {
            throw new IllegalArgumentException("metadatas list size must match ids list size if metadatas is provided.");
        }
        if (documents != null && documents.size() != size) {
            throw new IllegalArgumentException("documents list size must match ids list size if documents list is provided.");
        }
    }
}

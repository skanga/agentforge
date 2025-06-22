package com.skanga.observability.events;

import com.skanga.chat.messages.Message;
import com.skanga.rag.Document;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Event data for when a RAG agent receives results from a vector store similarity search.
 * This event is typically triggered after a {@link VectorStoreSearching} event.
 *
 * @param vectorStoreName The class name or identifier of the {@link com.skanga.rag.vectorstore.VectorStore}
 *                        that performed the search. Must not be null.
 * @param queryMessage    The user's query {@link Message} that initiated the search. Must not be null.
 *                        This helps correlate results with the original query.
 * @param documents       The list of {@link Document} objects retrieved from the vector store,
 *                        presumably ordered by relevance (highest score first). Must not be null;
 *                        can be empty if no results were found. A defensive copy is made.
 * @param durationMs      The duration of the vector store search operation in milliseconds.
 */
public record VectorStoreResult(
    String vectorStoreName,
    Message queryMessage,
    List<Document> documents,
    long durationMs
) {
    /**
     * Canonical constructor for VectorStoreResult.
     * Ensures vectorStoreName, queryMessage, and documents list are not null.
     * Makes a defensive copy of the documents list.
     */
    public VectorStoreResult {
        Objects.requireNonNull(vectorStoreName, "vectorStoreName cannot be null for VectorStoreResult event.");
        Objects.requireNonNull(queryMessage, "queryMessage cannot be null for VectorStoreResult event.");
        Objects.requireNonNull(documents, "documents list cannot be null for VectorStoreResult event.");
        if (durationMs < 0) {
            // Consider if duration can be unknown, e.g. -1.
            // throw new IllegalArgumentException("Duration cannot be negative.");
        }
        documents = Collections.unmodifiableList(new ArrayList<>(documents)); // Defensive copy
    }
}

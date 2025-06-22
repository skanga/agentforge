package com.skanga.rag.vectorstore;

import com.skanga.rag.Document;
import com.skanga.rag.vectorstore.search.SimilaritySearchUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * An in-memory implementation of the {@link VectorStore} interface.
 * Documents and their embeddings are all stored in memory.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Stores documents entirely in RAM.</li>
 *   <li>Similarity search is performed by iterating through all stored documents and
 *       calculating the cosine distance between the query embedding and each document's embedding.</li>
 *   <li>Suitable for small datasets, testing, or scenarios where persistence is not required.</li>
 *   <li>Not optimized for very large numbers of documents due to linear search time.</li>
 * </ul>
 * </p>
 *
 * <p><b>Thread Safety:</b>
 * Methods that modify the internal list of documents ({@code addDocument}, {@code addDocuments}, {@code clear})
 * are marked as {@code synchronized} to ensure basic thread safety if a single instance of
 * this store is shared across threads. The {@code similaritySearch} method is also synchronized
 * as it iterates over the shared list. For high-concurrency scenarios, more granular locking or
 * concurrent collections might be considered, but this provides a baseline.
 * </p>
 */
public class MemoryVectorStore implements VectorStore {

    /** The in-memory list holding the documents. */
    private final List<Document> documents;
    /** Default K value for similarity search if not specified by the caller (not directly used by search method). */
    private final int defaultTopK;

    /** Default value for K (number of results) if not specified in constructor. */
    private static final int DEFAULT_K_VALUE = 5;

    /**
     * Constructs a MemoryVectorStore with a default top-K value.
     * @see #DEFAULT_K_VALUE
     */
    public MemoryVectorStore() {
        this(DEFAULT_K_VALUE);
    }

    /**
     * Constructs a MemoryVectorStore with a specified default top-K value.
     * This default K is not directly used by {@link #similaritySearch(List, int)} which takes `k` as a parameter,
     * but can be a hint for default behavior if other search methods were added.
     *
     * @param defaultTopK The default number of similar documents to retrieve. Must be positive.
     * @throws IllegalArgumentException if defaultTopK is not positive.
     */
    public MemoryVectorStore(int defaultTopK) {
        if (defaultTopK <= 0) {
            throw new IllegalArgumentException("Default topK must be positive.");
        }
        // Use CopyOnWriteArrayList since reads vastly outnumber writes.
        this.documents = new CopyOnWriteArrayList<>();
        this.defaultTopK = defaultTopK;
    }

    /**
     * {@inheritDoc}
     * <p>The document's embedding must not be null or empty.</p>
     * <p>This operation is synchronized.</p>
     * @throws VectorStoreException if document is null, or its embedding is null/empty.
     */
    @Override
    public void addDocument(Document document) throws VectorStoreException {
        Objects.requireNonNull(document, "Document to add cannot be null.");
        if (document.getEmbedding() == null || document.getEmbedding().isEmpty()) {
            throw new VectorStoreException("Document embedding cannot be null or empty when adding to MemoryVectorStore. Doc ID: " + document.getId());
        }
        // Optional: Check for duplicate document ID to prevent issues,
        // though the interface doesn't strictly forbid it.
        // if (this.documents.stream().anyMatch(d -> d.getId().equals(document.getId()))) {
        //     throw new VectorStoreException("Document with ID " + document.getId() + " already exists in MemoryVectorStore.");
        // }
        this.documents.add(document);
    }

    /**
     * {@inheritDoc}
     * <p>Each document's embedding must not be null or empty.</p>
     * <p>This operation is synchronized.</p>
     * @throws VectorStoreException if documentsToAdd list is null, or any document therein is null
     *                              or has a null/empty embedding.
     */
    @Override
    public void addDocuments(List<Document> documentsToAdd) throws VectorStoreException {
        Objects.requireNonNull(documentsToAdd, "Documents list to add cannot be null.");
        for (Document doc : documentsToAdd) {
            // addDocument(doc) handles individual null checks and embedding presence.
            addDocument(doc);
        }
    }

    /**
     * {@inheritDoc}
     * <p>Performs a linear search through all stored documents, calculating cosine distance.
     * Results are sorted by similarity (higher score is better).
     * Documents without embeddings are skipped and a warning is logged.</p>
     * <p>This operation is synchronized.</p>
     * @throws IllegalArgumentException if k is not positive.
     * @throws VectorStoreException if queryEmbedding is null.
     */
    @Override
    public List<Document> similaritySearch(List<Double> queryEmbedding, int k) throws VectorStoreException {
        Objects.requireNonNull(queryEmbedding, "Query embedding cannot be null for similarity search.");
        if (k <= 0) {
            throw new IllegalArgumentException("Number of results to return (k) must be positive.");
        }

        if (this.documents.isEmpty()) {
            return Collections.emptyList();
        }

        List<DocumentDistancePair> documentDistances = new ArrayList<>();

        for (Document doc : this.documents) {
            if (doc.getEmbedding() == null || doc.getEmbedding().isEmpty()) {
                System.err.println("Warning: Document ID " + doc.getId() + " in MemoryVectorStore has no embedding and will be skipped in search.");
                continue;
            }
            try {
                double distance = SimilaritySearchUtils.cosineDistance(queryEmbedding, doc.getEmbedding());
                documentDistances.add(new DocumentDistancePair(doc, distance));
            } catch (VectorStoreException e) {
                // This could happen if, despite earlier checks, an embedding has a mismatched dimension.
                System.err.println("Warning: Could not calculate distance for document ID " + doc.getId() +
                                   " (embedding dim: " + doc.getEmbedding().size() +
                                   ", query dim: " + queryEmbedding.size() + "): " + e.getMessage());
            }
        }

        // Sort by distance (ascending, so smaller distance is better)
        documentDistances.sort(Comparator.comparingDouble(DocumentDistancePair::getDistance));

        // Get top k documents and set their scores.
        // Score is calculated as 1.0 - distance (cosine similarity).
        return documentDistances.stream()
                .limit(k)
                .map(pair -> {
                    // Cosine distance is in [0, 2]. Score = 1.0 - distance maps to [-1, 1].
                    // A score of 1.0 is most similar, -1.0 is most dissimilar.
                    double score = 1.0 - pair.getDistance();
                    pair.getDocument().setScore((float) score);
                    return pair.getDocument();
                })
                .collect(Collectors.toList());
    }

    /**
     * Helper inner class to temporarily store a document and its calculated distance
     * to the query vector, primarily for sorting.
     */
    private static class DocumentDistancePair {
        private final Document document;
        private final double distance; // Cosine distance (lower is better)

        public DocumentDistancePair(Document document, double distance) {
            this.document = document;
            this.distance = distance;
        }

        public Document getDocument() { return document; }
        public double getDistance() { return distance; }
    }

    /**
     * Returns a copy of all documents currently in the store.
     * For inspection or testing purposes.
     * This operation is synchronized.
     * @return A new list containing all documents.
     */
    public List<Document> getAllDocuments() {
        return new ArrayList<>(this.documents);
    }

    /**
     * Clears all documents from this in-memory vector store.
     * This operation is synchronized.
     */
    public void clear() {
        this.documents.clear();
    }
}

package com.skanga.rag.vectorstore;

import com.skanga.rag.Document;
import java.util.List;

/**
 * Interface for vector stores used in Retrieval Augmented Generation (RAG).
 * A vector store is responsible for storing {@link Document} objects along with their
 * vector embeddings, and providing functionality to search for documents similar
 * to a given query embedding.
 *
 * <p>Implementations can range from simple in-memory stores (e.g., {@link MemoryVectorStore})
 * to file-based stores (e.g., {@link FileVectorStore}) or integrations with dedicated
 * vector databases (e.g., Chroma, Pinecone, Elasticsearch).</p>
 */
public interface VectorStore {

    /**
     * Adds a single document to the vector store.
     * The document should typically have its embedding already generated and set
     * via {@link Document#setEmbedding(List)} before being added to the store,
     * as most vector stores expect pre-computed embeddings.
     *
     * @param document The {@link Document} object to add. Its 'embedding' field should be populated.
     * @throws VectorStoreException if an error occurs during the operation (e.g., I/O error,
     *                              database error, document validation failure like missing embedding).
     */
    void addDocument(Document document) throws VectorStoreException;

    /**
     * Adds a list of documents to the vector store.
     * Similar to {@link #addDocument(Document)}, each document in the list should ideally
     * have its embedding pre-computed. Some implementations might support batching
     * for more efficient addition.
     *
     * @param documents A list of {@link Document} objects to add.
     * @throws VectorStoreException if an error occurs during the operation.
     */
    void addDocuments(List<Document> documents) throws VectorStoreException;

    /**
     * Performs a similarity search against the documents in the vector store.
     *
     * @param queryEmbedding The vector embedding of the query text.
     * @param k              The number of top similar documents to retrieve.
     * @return A list of {@link Document} objects that are most similar to the query embedding.
     *         These documents should include their original content, metadata, and a similarity
     *         score (see {@link Document#setScore(float)}) indicating their relevance to the query.
     *         The list is typically sorted by relevance (highest score first).
     * @throws VectorStoreException if an error occurs during the search operation.
     */
    List<Document> similaritySearch(List<Double> queryEmbedding, int k) throws VectorStoreException;

    // --- Potential future enhancements for the interface ---
    // /**
    //  * Deletes documents from the vector store based on their IDs.
    //  * @param documentIds A list of IDs of documents to delete.
    //  * @throws VectorStoreException if an error occurs.
    //  */
    // void deleteDocuments(List<String> documentIds) throws VectorStoreException;

    // /**
    //  * Updates existing documents in the vector store.
    //  * Documents are typically identified by their ID.
    //  * @param documents A list of documents to update.
    //  * @throws VectorStoreException if an error occurs.
    //  */
    // void updateDocuments(List<Document> documents) throws VectorStoreException;

    // /**
    //  * Performs a similarity search using raw query text, implying the vector store
    //  * might handle embedding the query text itself using a configured EmbeddingProvider.
    //  * This is less common as embedding is often a separate concern.
    //  * @param queryText The raw query text.
    //  * @param k The number of top similar documents.
    //  * @return A list of similar documents.
    //  * @throws VectorStoreException if an error occurs.
    //  */
    // List<Document> similaritySearch(String queryText, int k) throws VectorStoreException;
}

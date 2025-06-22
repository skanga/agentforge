package com.skanga.rag.embeddings;

import com.skanga.rag.Document;
import java.util.List;

/**
 * Interface for embedding providers.
 * An embedding provider is responsible for generating vector embeddings (list of floats)
 * for textual input, which can be either raw strings or {@link Document} objects.
 *
 * Implementations of this interface will typically interact with external embedding model APIs
 * (e.g., OpenAI, Ollama, Voyage AI) or use local embedding models.
 */
public interface EmbeddingProvider {

    /**
     * Generates a vector embedding for a single piece of text.
     *
     * @param text The text to embed. Must not be null or empty.
     * @return A list of floats representing the embedding for the text.
     * @throws EmbeddingException if an error occurs during the embedding process
     *                            (e.g., API error, invalid input).
     */
    List<Double> embedText(String text) throws EmbeddingException;

    /**
     * Generates an embedding for a single {@link Document} and updates its embedding field.
     * The content of the document ({@link Document#getContent()}) is used for embedding.
     *
     * @param document The document to embed. Its content should not be null or empty.
     *                 The `embedding` field of this document will be populated.
     * @return The same document instance, updated with its vector embedding.
     * @throws EmbeddingException if an error occurs during embedding or if the document content is invalid.
     */
    Document embedDocument(Document document) throws EmbeddingException;

    /**
     * Generates embeddings for a list of {@link Document} objects and updates their embedding fields.
     * This method typically iterates through the documents, calling {@link #embedDocument(Document)}
     * for each, but subclasses may override it for batch embedding if supported by the provider.
     *
     * @param documents A list of {@link Document} objects to embed.
     *                  Each document's `embedding` field will be populated.
     * @return The same list of documents, with each document updated with its vector embedding.
     * @throws EmbeddingException if an error occurs during the embedding process for any document.
     */
    List<Document> embedDocuments(List<Document> documents) throws EmbeddingException;
}

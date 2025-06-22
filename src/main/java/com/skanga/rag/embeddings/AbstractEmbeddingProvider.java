package com.skanga.rag.embeddings;

import com.skanga.rag.Document;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList; // For creating a new list in embedDocuments


/**
 * Abstract base class for {@link EmbeddingProvider} implementations.
 * It provides default implementations for {@link #embedDocument(Document)} and
 * {@link #embedDocuments(List)}, which rely on the concrete subclass implementing
 * the {@link #embedText(String)} method.
 *
 * <p>Subclasses should:
 * <ul>
 *   <li>Implement {@link #embedText(String)} to call the specific embedding model/API.</li>
 *   <li>Optionally override {@link #embedDocuments(List)} if their underlying API supports
 *       more efficient batch embedding of multiple texts. The default implementation iterates
 *       and calls {@code embedDocument} for each document.</li>
 * </ul>
 * </p>
 */
public abstract class AbstractEmbeddingProvider implements EmbeddingProvider {

    /**
     * {@inheritDoc}
     * <p>This implementation retrieves the content from the document,
     * calls the abstract {@link #embedText(String)} method to get the embedding vector,
     * sets this vector on the document, and then returns the updated document.</p>
     *
     * @throws EmbeddingException if document or its content is null/empty, or if {@code embedText} fails.
     */
    @Override
    public Document embedDocument(Document document) throws EmbeddingException {
        Objects.requireNonNull(document, "Document to embed cannot be null.");
        if (document.getContent() == null || document.getContent().trim().isEmpty()) {
            // Or, could return the document as-is with an empty embedding,
            // but typically content is required for meaningful embedding.
            throw new EmbeddingException("Document content cannot be null or empty for embedding. Doc ID: " + document.getId());
        }
        List<Double> embeddingVector = this.embedText(document.getContent());
        document.setEmbedding(embeddingVector);
        return document;
    }

    /**
     * {@inheritDoc}
     * <p>This default implementation iterates through the provided list of documents
     * and calls {@link #embedDocument(Document)} for each one.
     * Subclasses are encouraged to override this method if their underlying embedding
     * service provides a more efficient batch embedding API.</p>
     *
     * @throws EmbeddingException if embedding fails for any document in the list.
     *                            The operation might be partially successful if some documents
     *                            were processed before an error on a subsequent one; however, this
     *                            implementation will stop and throw on the first error.
     */
    @Override
    public List<Document> embedDocuments(List<Document> documents) throws EmbeddingException {
        Objects.requireNonNull(documents, "Documents list to embed cannot be null.");
        // Return a new list containing the processed documents
        List<Document> resultDocuments = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            try {
                resultDocuments.add(embedDocument(doc)); // embedDocument updates the doc and returns it
            } catch (EmbeddingException e) {
                // Enrich exception with context or decide on error handling strategy (e.g., collect errors)
                throw new EmbeddingException("Failed to embed one or more documents in the batch. Error on doc ID '" +
                                             (doc != null ? doc.getId() : "unknown") + "': " + e.getMessage(), e.getCause() != null ? e.getCause() : e);
            }
        }
        return resultDocuments;
    }

    /**
     * {@inheritDoc}
     * <p>This method must be implemented by concrete subclasses to provide the actual
     * logic for converting a text string into a vector embedding using a specific
     * embedding model or API.</p>
     */
    @Override
    public abstract List<Double> embedText(String text) throws EmbeddingException;
}

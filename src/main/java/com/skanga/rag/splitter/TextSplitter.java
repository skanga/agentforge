package com.skanga.rag.splitter;

import com.skanga.rag.Document;
import java.util.List;

/**
 * Interface for text splitting strategies.
 * Implementations of this interface define how a large {@link Document}'s content
 * is broken down into smaller, more manageable chunks. Each chunk is typically
 * returned as a new {@code Document} object, often inheriting metadata from the parent.
 *
 * <p>Text splitting is a crucial step in RAG pipelines to ensure that text segments
 * fed to embedding models or LLMs fit within context window limits and are semantically coherent.</p>
 */
public interface TextSplitter {

    /**
     * Splits a single document into a list of smaller documents (chunks).
     * The original document's metadata, source type, and source name are typically
     * propagated to the generated chunk documents. Embeddings are usually cleared
     * as each chunk will need its own embedding.
     *
     * @param document The {@link Document} to split. Must not be null.
     * @return A list of {@link Document} objects, where each document represents a chunk
     *         of the original. If the document's content is small enough according to the
     *         splitter's logic, this list might contain a single document that is
     *         a copy of (or the same as) the original document.
     *         Returns an empty list or a list with one empty document if the input content is empty.
     */
    List<Document> splitDocument(Document document);

    /**
     * Splits a list of documents into smaller chunks.
     * This method typically iterates through the input list and applies {@link #splitDocument(Document)}
     * to each document, aggregating all resulting chunks into a single list.
     *
     * @param documents The list of {@link Document} objects to split. Must not be null.
     * @return A list containing all resulting chunk documents from splitting every document
     *         in the input list. Returns an empty list if the input list is empty.
     */
    List<Document> splitDocuments(List<Document> documents);
}

package com.skanga.rag.dataloader;

import com.skanga.rag.Document;
import java.io.IOException; // For getDocuments signature
import java.util.List;

/**
 * Interface for document loaders.
 * Document loaders are responsible for fetching or loading raw content from various sources
 * (e.g., file system, web URLs, databases, string content) and transforming this content
 * into a list of {@link com.skanga.rag.Document} objects.
 *
 * <p>Implementations of this interface might handle:
 * <ul>
 *   <li>Reading files of different types (text, PDF, etc.).</li>
 *   <li>Scraping web pages.</li>
 *   <li>Connecting to databases or APIs to fetch data.</li>
 *   <li>Processing raw string inputs.</li>
 * </ul>
 * </p>
 *
 * <p>Typically, after documents are loaded, they are passed to a
 * {@link com.skanga.rag.splitter.TextSplitter} to be broken down into smaller chunks
 * before embedding and indexing. This splitting logic is often part of the loader's
 * process or coordinated by a higher-level component (like the RAG agent itself).</p>
 */
public interface DocumentLoader {

    /**
     * Loads documents from the configured source.
     * The loaded documents might be raw (unsplit) or already split into chunks
     * depending on the loader's specific implementation and configuration (e.g.,
     * if it incorporates a {@link com.skanga.rag.splitter.TextSplitter}).
     *
     * @return A list of {@link Document} objects. This list should not be null;
     *         it can be empty if no documents are found or loaded.
     * @throws IOException if there's an I/O error during loading (e.g., file access issues).
     * @throws DocumentLoaderException if there's a specific error related to the document
     *                                 loading process itself (e.g., parsing errors,
     *                                 unsupported file types, configuration issues).
     */
    List<Document> getDocuments() throws IOException, DocumentLoaderException;
}

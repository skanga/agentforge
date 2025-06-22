package com.skanga.rag.vectorstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature; // For enabling indent output
import com.skanga.rag.Document;
import com.skanga.rag.vectorstore.search.SimilaritySearchUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets; // Specify charset
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

/**
 * A file-based implementation of the {@link VectorStore} interface.
 * Documents (including their embeddings and metadata) are stored as JSON objects,
 * one per line, in a single specified file (JSON-L format).
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Persists documents to the local file system.</li>
 *   <li>{@code addDocuments} appends new documents to the file.</li>
 *   <li>{@code similaritySearch} reads the entire file line by line, deserializes each document,
 *       calculates its similarity to the query, and uses a {@link PriorityQueue} to
 *       efficiently determine the top K most similar documents. This avoids loading all
 *       documents into memory at once if only their embeddings and scores are needed for sorting,
 *       though each document line is still read and deserialized.</li>
 * </ul>
 * </p>
 *
 * <p><b>Format:</b> Each line in the file is a JSON representation of a {@link Document} object.</p>
 *
 * <p><b>Thread Safety:</b>
 * Methods that modify the file ({@code addDocument}, {@code addDocuments}, {@code clear}) are
 * marked as {@code synchronized} on the instance to prevent concurrent writes to the same file
 * from within the same JVM process. The {@code similaritySearch} method is not synchronized
 * as it's read-only, but relies on the file content not changing during its execution for consistency.
 * This store is not designed for inter-process concurrency on the same file.
 * </p>
 *
 * <p><b>Performance:</b>
 * For very large datasets, this implementation's search performance will degrade as it needs
 * to scan and deserialize all documents. It's best suited for small to medium-sized collections
 * where simplicity of a file-based store is desired. For larger scale, dedicated vector databases
 * (like Chroma, Elasticsearch, Pinecone) are recommended.
 * </p>
 */
public class FileVectorStore implements VectorStore {

    private final Path filePath;
    /** Default K value for similarity search if not specified by the caller (not directly used by search method). */
    private final int defaultTopK;
    private final ObjectMapper objectMapper;

    /** Default value for K (number of results) if not specified in constructor. */
    private static final int DEFAULT_K_FILE_STORE = 5;
    /** Default file name if only a directory is provided for storage. */
    public static final String DEFAULT_VECTOR_STORE_FILENAME = "vector_store.jsonl";


    /**
     * Constructs a FileVectorStore.
     *
     * @param directoryPath The path to the directory where the vector store file will be located.
     *                      The directory will be created if it doesn't exist.
     * @param fileName      The name of the file to store the vectors (e.g., "my_vectors.jsonl").
     * @param defaultTopK   A default value for 'k' (top results) if certain methods were to use it
     *                      (currently, `similaritySearch` requires explicit `k`). Must be positive.
     * @throws VectorStoreException if the directory cannot be created or the file cannot be initialized.
     */
    public FileVectorStore(String directoryPath, String fileName, int defaultTopK) throws VectorStoreException {
        Objects.requireNonNull(directoryPath, "Directory path cannot be null.");
        Objects.requireNonNull(fileName, "File name cannot be null.");
        if (defaultTopK <= 0) {
            throw new IllegalArgumentException("defaultTopK must be positive.");
        }

        this.filePath = Paths.get(directoryPath, fileName);
        this.defaultTopK = defaultTopK;
        this.objectMapper = new ObjectMapper();
        // Configure for potentially pretty-printing in file, though not strictly necessary for JSON-L
        // this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);


        try {
            Path parentDir = this.filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            // Ensure file exists, or create it if it doesn't, so append mode works correctly.
            if (!Files.exists(this.filePath)) {
                Files.createFile(this.filePath);
            }
        } catch (IOException e) {
            throw new VectorStoreException("Failed to initialize FileVectorStore at path: " + this.filePath, e);
        }
    }

    /**
     * Constructs a FileVectorStore with a default top-K value.
     * @param directoryPath The directory path.
     * @param fileName      The file name.
     * @throws VectorStoreException if initialization fails.
     */
    public FileVectorStore(String directoryPath, String fileName) throws VectorStoreException {
        this(directoryPath, fileName, DEFAULT_K_FILE_STORE);
    }

    /**
     * Gets the path to the file used by this vector store.
     * @return The file path.
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * {@inheritDoc}
     * <p>This operation is synchronized.</p>
     */
    @Override
    public synchronized void addDocument(Document document) throws VectorStoreException {
        addDocuments(Collections.singletonList(document));
    }

    /**
     * {@inheritDoc}
     * <p>Appends documents as JSON lines to the configured file. Each document's embedding must be pre-populated.
     * This operation is synchronized.</p>
     * @throws VectorStoreException if documents list is null, any document is null, a document misses embedding,
     *                              or if JSON serialization or file I/O fails.
     */
    @Override
    public synchronized void addDocuments(List<Document> documentsToAdd) throws VectorStoreException {
        Objects.requireNonNull(documentsToAdd, "Documents list to add cannot be null.");
        if (documentsToAdd.isEmpty()) {
            return;
        }

        // Using try-with-resources for BufferedWriter ensures it's closed.
        // APPEND ensures we add to the file; CREATE ensures file exists.
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8, StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
            for (Document doc : documentsToAdd) {
                Objects.requireNonNull(doc, "Document in list cannot be null.");
                if (doc.getEmbedding() == null || doc.getEmbedding().isEmpty()) {
                    throw new VectorStoreException("Document embedding cannot be null or empty when adding to FileVectorStore. Doc ID: " + doc.getId());
                }
                String jsonDocument = objectMapper.writeValueAsString(doc);
                writer.write(jsonDocument);
                writer.newLine();
            }
        } catch (JsonProcessingException e) {
            throw new VectorStoreException("Failed to serialize document to JSON for file storage.", e);
        } catch (IOException e) {
            throw new VectorStoreException("Failed to write documents to file: " + filePath, e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>Reads documents line by line from the file, calculates cosine distance, and uses a
     * min-priority queue (implemented as a max-heap of distances) to find the top K results efficiently
     * in terms of memory for sorting, though all document lines are still parsed.</p>
     * @throws IllegalArgumentException if k is not positive.
     * @throws VectorStoreException if queryEmbedding is null, or if file reading or JSON deserialization fails.
     */
    @Override
    public List<Document> similaritySearch(List<Double> queryEmbedding, int k) throws VectorStoreException {
        Objects.requireNonNull(queryEmbedding, "Query embedding cannot be null for similarity search.");
        if (k <= 0) {
            throw new IllegalArgumentException("Number of results to return (k) must be positive.");
        }

        // Max-heap for distances to keep the k smallest distances (closest documents)
        // The comparator makes it behave as a max-heap for distances.
        PriorityQueue<DocumentDistancePair> topKQueue = new PriorityQueue<>(k, Comparator.comparingDouble(DocumentDistancePair::getDistance).reversed());

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue; // Skip empty lines

                Document doc;
                try {
                    doc = objectMapper.readValue(line, Document.class);
                } catch (JsonProcessingException e) {
                    System.err.println("Warning: Failed to deserialize document from file line: \"" + line + "\". Error: " + e.getMessage());
                    continue; // Skip malformed lines
                }

                if (doc.getEmbedding() == null || doc.getEmbedding().isEmpty()) {
                     System.err.println("Warning: Document ID " + doc.getId() + " in FileVectorStore has no embedding and will be skipped in search.");
                    continue;
                }

                try {
                    double distance = SimilaritySearchUtils.cosineDistance(queryEmbedding, doc.getEmbedding());
                    if (topKQueue.size() < k) {
                        topKQueue.add(new DocumentDistancePair(doc, distance));
                    } else if (distance < topKQueue.peek().getDistance()) { // If new distance is smaller than the largest in queue
                        topKQueue.poll(); // Remove the one with largest distance (smallest similarity)
                        topKQueue.add(new DocumentDistancePair(doc, distance));
                    }
                } catch (VectorStoreException e) { // Catch errors from cosineDistance (e.g. dimension mismatch)
                     System.err.println("Warning: Could not calculate distance for document ID " + doc.getId() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new VectorStoreException("Failed to read documents from file: " + filePath, e);
        }

        // Convert queue to a list, sort by distance (ascending), and set scores
        List<DocumentDistancePair> resultPairs = new ArrayList<>(topKQueue);
        resultPairs.sort(Comparator.comparingDouble(DocumentDistancePair::getDistance));

        return resultPairs.stream()
                .map(pair -> {
                    double score = 1.0 - pair.getDistance(); // Score is cosine similarity
                    pair.getDocument().setScore((float) score);
                    return pair.getDocument();
                })
                .collect(Collectors.toList());
    }

    /**
     * Helper inner class to temporarily store a document and its calculated distance.
     */
    private static class DocumentDistancePair {
        private final Document document;
        private final double distance;

        public DocumentDistancePair(Document document, double distance) {
            this.document = document;
            this.distance = distance;
        }
        public Document getDocument() { return document; }
        public double getDistance() { return distance; }
    }

    /**
     * Clears all documents from this file-based vector store by truncating the underlying file.
     * This operation is synchronized.
     * @throws VectorStoreException if an I/O error occurs.
     */
    public synchronized void clear() throws VectorStoreException {
        try {
            // Truncate existing file or create if it doesn't exist (though constructor should ensure creation)
            Files.write(filePath, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new VectorStoreException("Failed to clear vector store file: " + filePath, e);
        }
    }
}

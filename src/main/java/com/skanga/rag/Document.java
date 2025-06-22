package com.skanga.rag;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a piece of text content used in Retrieval Augmented Generation (RAG).
 * A Document typically contains a chunk of text, its vector embedding, metadata,
 * and information about its source. It's a fundamental unit for vector stores
 * and retrieval processes.
 *
 * <p>Key aspects:
 * <ul>
 *   <li>An {@code id} is automatically generated (UUID) upon creation.</li>
 *   <li>{@code embedding} stores the vector representation of the content.</li>
 *   <li>{@code metadata} allows storing arbitrary additional information.</li>
 *   <li>{@code score} can be used by vector stores or post-processors to indicate relevance.</li>
 * </ul>
 * This class is designed to be Jackson-serializable for persistence or API communication.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // Don't serialize null fields
public class Document {

    /** Unique identifier for the document, typically a UUID. */
    @JsonProperty("id")
    private String id;

    /** The main textual content of the document or document chunk. */
    @JsonProperty("content")
    private String content;

    /**
     * The vector embedding of the {@link #content}.
     * This is a list of floats representing the document in a high-dimensional space.
     * Initialized to an empty list; should be populated by an {@link com.skanga.rag.embeddings.EmbeddingProvider}.
     */
    @JsonProperty("embedding")
    private List<Double> embedding;

    /**
     * The type of the source from which this document originated (e.g., "file", "url", "manual").
     * Defaults to "manual".
     */
    @JsonProperty("source_type")
    private String sourceType;

    /**
     * The specific name or identifier of the source (e.g., file path, URL).
     * Defaults to "manual".
     */
    @JsonProperty("source_name")
    private String sourceName;

    /**
     * A score associated with this document, often assigned during a similarity search
     * or by a reranking process. Higher scores typically indicate greater relevance.
     * Defaults to 0.0f.
     */
    @JsonProperty("score")
    private float score;

    /**
     * A map for storing arbitrary metadata associated with the document
     * (e.g., creation date, author, specific fields extracted during parsing).
     * Initialized to an empty map.
     */
    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    /**
     * Constructs a new Document with the given content.
     * An ID is automatically generated. Other fields are set to default values.
     *
     * @param content The textual content of this document. Must not be null.
     */
    @JsonCreator // Useful for Jackson if it needs to pick a constructor
    public Document(@JsonProperty("content") String content) { // content is required by this constructor
        Objects.requireNonNull(content, "Document content cannot be null.");
        this.id = UUID.randomUUID().toString();
        this.content = content;
        this.embedding = new ArrayList<>();
        this.sourceType = "manual";
        this.sourceName = "manual";
        this.score = 0.0f;
        this.metadata = new HashMap<>();
    }

    /**
     * Default constructor, mainly for deserialization frameworks like Jackson.
     * It's generally recommended to use {@link #Document(String)} for manual creation
     * as content is fundamental. If this is used, ensure content and other essential fields
     * are set afterwards.
     */
    public Document() {
        this.id = UUID.randomUUID().toString();
        this.embedding = new ArrayList<>();
        this.sourceType = "manual";
        this.sourceName = "manual";
        this.score = 0.0f;
        this.metadata = new HashMap<>();
    }


    // --- Getters ---
    /** @return The unique ID of this document. */
    public String getId() { return id; }
    /** @return The textual content of this document. */
    public String getContent() { return content; }
    /** @return The vector embedding of the content (list of floats). May be empty if not yet embedded. */
    public List<Double> getEmbedding() { return Collections.unmodifiableList(embedding); }
    /** @return The type of the source (e.g., "file", "url"). */
    public String getSourceType() { return sourceType; }
    /** @return The name or identifier of the source (e.g., file path). */
    public String getSourceName() { return sourceName; }
    /** @return The relevance score assigned to this document. */
    public float getScore() { return score; }
    /** @return The metadata map associated with this document. */
    public Map<String, Object> getMetadata() { return Collections.unmodifiableMap(metadata); }

    // --- Setters ---
    /** Sets the ID of this document. Usually set automatically. */
    public void setId(String id) { this.id = Objects.requireNonNull(id, "ID cannot be null."); }
    /** Sets the textual content of this document. */
    public void setContent(String content) { this.content = Objects.requireNonNull(content, "Content cannot be null."); }
    /** Sets the vector embedding for this document. A defensive copy is made. */
    public void setEmbedding(List<Double> embedding) { this.embedding = (embedding == null) ? new ArrayList<>() : new ArrayList<>(embedding); }
    /** Sets the source type. */
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    /** Sets the source name. */
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    /** Sets the relevance score. */
    public void setScore(float score) { this.score = score; }
    /** Sets the entire metadata map. A defensive copy is made. */
    public void setMetadata(Map<String, Object> metadata) { this.metadata = (metadata == null) ? new HashMap<>() : new HashMap<>(metadata); }

    // --- Other methods ---
    /**
     * Adds a key-value pair to the document's metadata.
     * If the key already exists, its value will be overwritten.
     * @param key The metadata key. Must not be null.
     * @param value The metadata value.
     */
    public void addMetadata(String key, Object value) {
        Objects.requireNonNull(key, "Metadata key cannot be null.");
        if (this.metadata == null) { // Should be initialized by constructor, but defensive
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    @Override
    public String toString() {
        String contentPreview = content;
        if (content != null && content.length() > 70) {
            contentPreview = content.substring(0, 67) + "...";
        }
        contentPreview = (contentPreview != null) ? contentPreview.replace("\n", "\\n") : "null";


        return "Document{" +
                "id='" + id + '\'' +
                ", content='" + contentPreview + '\'' +
                ", embedding_size=" + (embedding != null ? embedding.size() : "0") +
                ", sourceType='" + sourceType + '\'' +
                ", sourceName='" + sourceName + '\'' +
                ", score=" + score +
                ", metadata_keys=" + (metadata != null ? metadata.keySet() : "null") +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Document document = (Document) o;
        return Float.compare(document.score, score) == 0 &&
               Objects.equals(id, document.id) &&
               Objects.equals(content, document.content) &&
               Objects.equals(embedding, document.embedding) &&
               Objects.equals(sourceType, document.sourceType) &&
               Objects.equals(sourceName, document.sourceName) &&
               Objects.equals(metadata, document.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, content, embedding, sourceType, sourceName, score, metadata);
    }
}

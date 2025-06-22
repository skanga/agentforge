package com.skanga.rag.dataloader;

import com.skanga.rag.Document;
import com.skanga.rag.splitter.TextSplitter;
import java.io.IOException; // Included for interface compliance, though this loader doesn't do IO.
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A simple {@link DocumentLoader} that creates documents directly from a provided String.
 * This is useful for loading content that is already available in memory, or for testing.
 *
 * <p>The loaded document will have its {@code sourceType} set to "string" and its
 * {@code sourceName} can be optionally specified.</p>
 *
 * <p>Like other loaders extending {@link AbstractDocumentLoader}, it applies the configured
 * {@link TextSplitter} to the created document(s).</p>
 */
public class StringDocumentLoader extends AbstractDocumentLoader {

    private final String content;
    private final String sourceName;

    /**
     * Default source name if none is provided.
     */
    public static final String DEFAULT_STRING_SOURCE_NAME = "string_input";

    /**
     * Constructs a StringDocumentLoader with the given content.
     * The source name defaults to {@link #DEFAULT_STRING_SOURCE_NAME}.
     * Uses the default {@link TextSplitter} from {@link AbstractDocumentLoader}.
     *
     * @param content The string content to load. Must not be null.
     */
    public StringDocumentLoader(String content) {
        this(content, DEFAULT_STRING_SOURCE_NAME);
    }

    /**
     * Constructs a StringDocumentLoader with the given content and a specific source name.
     * Uses the default {@link TextSplitter} from {@link AbstractDocumentLoader}.
     *
     * @param content    The string content to load. Must not be null.
     * @param sourceName A name to identify the source of this string content. Must not be null.
     */
    public StringDocumentLoader(String content, String sourceName) {
        super(); // Uses default TextSplitter
        this.content = Objects.requireNonNull(content, "Content string cannot be null.");
        this.sourceName = Objects.requireNonNull(sourceName, "Source name cannot be null.");
    }

    /**
     * Constructs a StringDocumentLoader with specified content, source name, and text splitter.
     *
     * @param content      The string content to load. Must not be null.
     * @param sourceName   A name to identify the source of this string content. Must not be null.
     * @param textSplitter The {@link TextSplitter} to use for splitting the document. Must not be null.
     */
    public StringDocumentLoader(String content, String sourceName, TextSplitter textSplitter) {
        super(textSplitter);
        this.content = Objects.requireNonNull(content, "Content string cannot be null.");
        this.sourceName = Objects.requireNonNull(sourceName, "Source name cannot be null.");
    }


    /**
     * {@inheritDoc}
     * <p>Creates a single {@link Document} from the provided string content,
     * then applies the configured {@link TextSplitter} to it.
     * The document's {@code sourceType} will be "string" and {@code sourceName}
     * will be as configured in the constructor.</p>
     *
     * @throws DocumentLoaderException (though unlikely for this simple loader unless text splitter fails).
     * @throws IOException (for interface compliance, not directly thrown by this implementation).
     */
    @Override
    public List<Document> getDocuments() throws IOException, DocumentLoaderException {
        Document document = new Document(this.content);
        document.setSourceType("string");
        document.setSourceName(this.sourceName);
        // Example of adding specific metadata:
        // document.addMetadata("original_content_length", this.content.length());

        // Apply text splitting to the single document created from the string
        if (this.textSplitter != null) {
            return this.textSplitter.splitDocument(document);
        } else {
            // Should not happen if AbstractDocumentLoader ensures a default splitter
            return Collections.singletonList(document);
        }
    }
}

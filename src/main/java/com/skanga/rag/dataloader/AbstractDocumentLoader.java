package com.skanga.rag.dataloader;

import com.skanga.rag.splitter.DelimiterTextSplitter;
import com.skanga.rag.splitter.TextSplitter;
import java.util.Objects;

/**
 * An abstract base class for {@link DocumentLoader} implementations.
 * This class provides common functionality related to text splitting by holding
 * an instance of {@link TextSplitter}.
 *
 * <p>Concrete document loader subclasses (e.g., {@link FileSystemDocumentLoader}, {@link StringDocumentLoader})
 * are responsible for implementing the {@link #getDocuments()} method to load raw documents
 * from their specific source. They can then use the configured {@code TextSplitter}
 * (available via {@link #getTextSplitter()}) to split the loaded documents into smaller chunks
 * if necessary, typically by calling {@code textSplitter.splitDocuments(rawDocs)} or
 * {@code textSplitter.splitDocument(rawDoc)}.</p>
 *
 * <p>A default {@link DelimiterTextSplitter} is initialized with general-purpose settings.
 * This can be overridden by providing a custom {@code TextSplitter} via the constructor
 * or the {@link #withTextSplitter(TextSplitter)} method.</p>
 */
public abstract class AbstractDocumentLoader implements DocumentLoader {

    /** The text splitter to be used by subclasses for splitting loaded document content. */
    protected TextSplitter textSplitter;

    /**
     * Default constructor. Initializes with a default {@link DelimiterTextSplitter}.
     * The default splitter uses a max length of 1500 characters, separates by double newlines ("\n\n"),
     * and has a word overlap of 100 words.
     */
    protected AbstractDocumentLoader() {
        this.textSplitter = new DelimiterTextSplitter(1500, "\n\n", 100);
    }

    /**
     * Constructs an AbstractDocumentLoader with a specific {@link TextSplitter}.
     * @param textSplitter The text splitter to use. Must not be null.
     */
    protected AbstractDocumentLoader(TextSplitter textSplitter) {
        this.textSplitter = Objects.requireNonNull(textSplitter, "TextSplitter cannot be null.");
    }

    /**
     * Sets a custom text splitter for this document loader.
     * Allows overriding the default splitter.
     *
     * @param textSplitter The {@link TextSplitter} to use. Must not be null.
     * @return This {@code AbstractDocumentLoader} instance for fluent chaining.
     */
    public AbstractDocumentLoader withTextSplitter(TextSplitter textSplitter) {
        this.textSplitter = Objects.requireNonNull(textSplitter, "TextSplitter to set cannot be null.");
        return this;
    }

    /**
     * Gets the currently configured {@link TextSplitter}.
     * @return The text splitter instance.
     */
    public TextSplitter getTextSplitter() {
        return textSplitter;
    }
}

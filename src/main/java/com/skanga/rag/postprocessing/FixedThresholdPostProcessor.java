package com.skanga.rag.postprocessing;

import com.skanga.chat.messages.Message;
import com.skanga.rag.Document;

import java.util.Collections; // For Collections.emptyList()
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A {@link PostProcessor} that filters a list of {@link Document} objects
 * based on a fixed score threshold. Only documents with a score greater than
 * or equal to the specified threshold are retained.
 *
 * <p>This processor is useful for quickly culling documents that fall below a
 * minimum relevance score after an initial retrieval phase.</p>
 */
public class FixedThresholdPostProcessor implements PostProcessor {

    /** The score threshold. Documents with scores below this value will be filtered out. */
    private final float threshold;

    /**
     * Creates a {@code FixedThresholdPostProcessor}.
     *
     * @param threshold The minimum score a document must have to be kept.
     *                  Scores are typically produced by vector store similarity searches
     *                  (e.g., cosine similarity where higher is better, often in [0,1] or [-1,1] range)
     *                  or by rerankers.
     */
    public FixedThresholdPostProcessor(float threshold) {
        this.threshold = threshold;
    }

    /**
     * {@inheritDoc}
     * <p>This implementation filters the input {@code documents} list, returning only
     * those documents whose {@link Document#getScore()} is greater than or equal to
     * the threshold set during construction.</p>
     * <p>The {@code question} parameter is not used by this specific post-processor.</p>
     */
    @Override
    public List<Document> process(Message question, List<Document> documents) throws PostProcessorException {
        // Question is not used by this simple threshold filter, but is part of the interface.
        Objects.requireNonNull(documents, "Input documents list cannot be null for FixedThresholdPostProcessor.");

        if (documents.isEmpty()) {
            return Collections.emptyList();
        }

        return documents.stream()
                .filter(doc -> doc.getScore() >= this.threshold)
                .collect(Collectors.toList());
    }
}

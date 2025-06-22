package com.skanga.rag.postprocessing;

import com.skanga.chat.messages.Message;
import com.skanga.rag.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A {@link PostProcessor} that filters documents based on an adaptive threshold.
 * The threshold is calculated dynamically from the scores of the input documents:
 * {@code threshold = mean_score - (factor * standard_deviation_of_scores)}.
 *
 * <p>This processor also ensures that a minimum number of documents ({@code minDocs})
 * are returned if possible, even if their scores fall below the calculated dynamic threshold.
 * In such cases, it returns the top {@code minDocs} documents sorted by their original scores.</p>
 *
 * <p>This approach can be more robust than a fixed threshold when document scores vary
 * significantly across different queries or datasets.</p>
 */
public class AdaptiveThresholdPostProcessor implements PostProcessor {

    /**
     * Factor to multiply by the standard deviation. A higher factor makes the
     * dynamic threshold stricter (i.e., higher, retaining fewer documents unless `minDocs` kicks in).
     */
    private final double factor;
    /**
     * Minimum number of documents to return if available, overriding the dynamic threshold
     * if it would result in fewer documents.
     */
    private final int minDocs;

    /**
     * Creates an {@code AdaptiveThresholdPostProcessor}.
     *
     * @param factor  The factor to multiply by the standard deviation when calculating the threshold.
     *                Common values might range from 0.5 to 2.0.
     * @param minDocs The minimum number of documents to return. If the number of documents
     *                passing the dynamic threshold is less than {@code minDocs}, the top {@code minDocs}
     *                (by original score, descending) will be returned from the input list,
     *                provided enough documents were input. Set to 0 if no minimum is desired.
     * @throws IllegalArgumentException if {@code minDocs} is negative.
     */
    public AdaptiveThresholdPostProcessor(double factor, int minDocs) {
        if (minDocs < 0) {
            throw new IllegalArgumentException("Minimum number of documents (minDocs) cannot be negative.");
        }
        this.factor = factor;
        this.minDocs = minDocs;
    }

    /**
     * {@inheritDoc}
     * <p>This implementation calculates a dynamic score threshold based on the mean and
     * standard deviation of the input documents' scores. It then filters documents by this
     * threshold, additionally ensuring that at least {@code minDocs} are returned if available
     * and the filter results in fewer documents.</p>
     * <p>The {@code question} parameter is not used by this specific post-processor.</p>
     */
    @Override
    public List<Document> process(Message question, List<Document> documents) throws PostProcessorException {
        Objects.requireNonNull(documents, "Input documents list cannot be null for AdaptiveThresholdPostProcessor.");

        if (documents.isEmpty()) {
            return Collections.emptyList();
        }

        List<Float> scores = documents.stream()
                .map(Document::getScore)
                .collect(Collectors.toList());

        // If too few documents for meaningful statistics, or if fewer docs than minDocs.
        if (scores.size() < 2) {
            if (documents.size() <= minDocs && minDocs > 0) { // If we want minDocs and have fewer/equal total
                // Sort by score just in case, though with 0 or 1 doc, it's trivial.
                return documents.stream()
                           .sorted(Comparator.comparingDouble(Document::getScore).reversed())
                           .limit(Math.min(documents.size(), minDocs)) // Ensure we don't request more than available
                           .collect(Collectors.toList());
            }
            // If minDocs is 0, or scores.size() >= minDocs (e.g. minDocs=1, scores.size=1),
            // adaptive thresholding doesn't make sense or apply simply. Return as is or based on simple minDocs.
            // This path means either minDocs is 0 and scores < 2, or minDocs is >= scores.size().
            // If minDocs is the constraint, ensure it's sorted.
            if (minDocs > 0) {
                 return documents.stream()
                           .sorted(Comparator.comparingDouble(Document::getScore).reversed())
                           .limit(Math.min(documents.size(), minDocs))
                           .collect(Collectors.toList());
            }
            return new ArrayList<>(documents); // Return original (or copy) if no stats and no minDocs constraint
        }

        double sum = 0.0;
        for (float score : scores) {
            sum += score;
        }
        double mean = sum / scores.size();

        double variance = 0.0;
        for (float score : scores) {
            variance += Math.pow(score - mean, 2);
        }
        variance /= scores.size(); // Population variance
        double stdDev = Math.sqrt(variance);

        double dynamicThreshold = mean - (this.factor * stdDev);

        List<Document> filteredDocuments = documents.stream()
                .filter(doc -> doc.getScore() >= dynamicThreshold)
                .sorted(Comparator.comparingDouble(Document::getScore).reversed()) // Keep best of filtered
                .collect(Collectors.toList());

        // Ensure minDocs constraint is met if necessary
        if (filteredDocuments.size() < this.minDocs) {
            // Not enough documents passed the threshold, but we need to return at least minDocs if available.
            // Sort the original list and take the top minDocs.
            // This ensures we return the best available documents up to minDocs.
            if (documents.size() >= this.minDocs) {
                return documents.stream()
                        .sorted(Comparator.comparingDouble(Document::getScore).reversed())
                        .limit(this.minDocs)
                        .collect(Collectors.toList());
            } else {
                // We have fewer total documents than minDocs, so return all of them, sorted.
                 return documents.stream()
                        .sorted(Comparator.comparingDouble(Document::getScore).reversed())
                        .collect(Collectors.toList());
            }
        } else if (filteredDocuments.isEmpty() && this.minDocs == 0) {
            // No documents passed, and minDocs is 0, return empty list.
            return Collections.emptyList();
        }

        return filteredDocuments; // Already sorted if it came from the filter path
    }
}

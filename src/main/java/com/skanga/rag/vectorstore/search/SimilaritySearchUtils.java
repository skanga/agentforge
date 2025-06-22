package com.skanga.rag.vectorstore.search;

import com.skanga.rag.vectorstore.VectorStoreException;
import java.util.List;
import java.util.Objects;

/**
 * Utility class providing common similarity search functions, such as distance calculations.
 */
public class SimilaritySearchUtils {

    /**
     * Private constructor to prevent instantiation of this utility class.
     * All methods are static.
     */
    private SimilaritySearchUtils() {}

    /**
     * Calculates the cosine distance between two vectors.
     * Cosine distance is defined as {@code 1 - cosine_similarity}.
     * A lower distance indicates higher similarity (with 0.0 meaning identical direction).
     *
     * <p>The vectors must be non-null, non-empty, and of the same dimension.</p>
     *
     * <p>Calculation steps:
     * <ol>
     *   <li>Validate inputs.</li>
     *   <li>Calculate the dot product of the two vectors.</li>
     *   <li>Calculate the L2 norm (magnitude) of each vector.</li>
     *   <li>Compute cosine similarity: {@code dotProduct / (normA * normB)}.</li>
     *   <li>Return {@code 1.0 - similarity}.</li>
     * </ol>
     * If either vector has a magnitude of 0, cosine similarity is undefined or considered 0.
     * In such cases, or if vectors are orthogonal and similarity is 0, the distance is 1.0.
     * If vectors point in opposite directions, similarity is -1, and distance is 2.0.
     * </p>
     *
     * @param vector1 The first vector (list of floats).
     * @param vector2 The second vector (list of floats).
     * @return The cosine distance as a {@code double}, ranging from 0.0 (most similar) to 2.0 (most dissimilar).
     *         Returns 1.0 if one or both vectors are effectively zero vectors leading to undefined similarity,
     *         or if an error in calculation occurs that might lead to NaN (though checks aim to prevent this).
     * @throws VectorStoreException if vectors are null, empty, or have different lengths.
     */
    public static double cosineDistance(List<Double> vector1, List<Double> vector2) throws VectorStoreException {
        Objects.requireNonNull(vector1, "Vector1 cannot be null for cosine distance calculation.");
        Objects.requireNonNull(vector2, "Vector2 cannot be null for cosine distance calculation.");

        if (vector1.size() != vector2.size()) {
            throw new VectorStoreException("Vectors must have the same dimension for cosine distance. " +
                                           "Vector1 dim: " + vector1.size() + ", Vector2 dim: " + vector2.size());
        }
        if (vector1.isEmpty()) {
             throw new VectorStoreException("Vectors cannot be empty for cosine distance calculation.");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vector1.size(); i++) {
            // Assuming list elements are non-null. If they can be null, add null checks.
            // Float.floatValue() is not needed if directly using float from List<Float>.
            double val1 = vector1.get(i);
            double val2 = vector2.get(i);

            dotProduct += val1 * val2;
            normA += val1 * val1;
            normB += val2 * val2;
        }

        // Check for zero vectors to avoid division by zero and NaN results.
        // If either vector magnitude is zero, cosine similarity is undefined or 0.
        // A distance of 1.0 implies no similarity (orthogonal or one/both zero).
        if (normA == 0.0 || normB == 0.0) {
            // If both are true zero vectors, they are identical in a sense, so distance could be 0.
            // However, typically similarity with a zero vector is taken as 0.
            // Let's return 1.0 (1 - 0) to indicate no directional similarity if at least one is zero.
            // This also handles cases where one vector is non-zero and the other is zero.
            return 1.0;
        }

        double similarity = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));

        // Clamp similarity to [-1, 1] to handle potential floating-point inaccuracies for near-collinear vectors.
        similarity = Math.max(-1.0, Math.min(1.0, similarity));

        return 1.0 - similarity;
    }
}

package com.skanga.rag.vectorstore.search;

import com.skanga.rag.vectorstore.VectorStoreException;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SimilaritySearchUtilsTests {

    private static final double EPSILON = 1e-6; // For float/double comparisons

    @Test
    void cosineDistance_identicalVectors_returnsZero() {
        List<Double> v1 = Arrays.asList(1.0, 2.0, 3.0);
        List<Double> v2 = Arrays.asList(1.0, 2.0, 3.0);
        assertEquals(0.0, SimilaritySearchUtils.cosineDistance(v1, v2), EPSILON);
    }

    @Test
    void cosineDistance_oppositeVectors_returnsTwo() {
        List<Double> v1 = Arrays.asList(1.0, 2.0, 3.0);
        List<Double> v2 = Arrays.asList(-1.0, -2.0, -3.0);
        assertEquals(2.0, SimilaritySearchUtils.cosineDistance(v1, v2), EPSILON);
    }

    @Test
    void cosineDistance_orthogonalVectors_returnsOne() {
        List<Double> v1 = Arrays.asList(1.0, 0.0);
        List<Double> v2 = Arrays.asList(0.0, 1.0);
        assertEquals(1.0, SimilaritySearchUtils.cosineDistance(v1, v2), EPSILON);
    }

    @Test
    void cosineDistance_generalCase_isCorrect() {
        List<Double> v1 = Arrays.asList(1.0, 2.0, 3.0, 4.0);
        List<Double> v2 = Arrays.asList(4.0, 1.0, 2.0, 3.0);
        // Dot product = 1*4 + 2*1 + 3*2 + 4*3 = 4 + 2 + 6 + 12 = 24
        // NormA = sqrt(1+4+9+16) = sqrt(30)
        // NormB = sqrt(16+1+4+9) = sqrt(30)
        // Similarity = 24 / (sqrt(30) * sqrt(30)) = 24 / 30 = 0.8
        // Distance = 1 - 0.8 = 0.2
        assertEquals(0.2, SimilaritySearchUtils.cosineDistance(v1, v2), EPSILON);
    }

    @Test
    void cosineDistance_scaledVectors_returnsZero() {
        List<Double> v1 = Arrays.asList(1.0, 1.0, 1.0);
        List<Double> v2 = Arrays.asList(5.0, 5.0, 5.0);
        assertEquals(0.0, SimilaritySearchUtils.cosineDistance(v1, v2), EPSILON,
            "Cosine distance should be 0 for vectors pointing in the same direction, regardless of magnitude.");
    }


    @Test
    void cosineDistance_oneVectorIsZero_returnsOne() {
        List<Double> v1 = Arrays.asList(1.0, 2.0, 3.0);
        List<Double> v0 = Arrays.asList(0.0, 0.0, 0.0);
        assertEquals(1.0, SimilaritySearchUtils.cosineDistance(v1, v0), EPSILON);
        assertEquals(1.0, SimilaritySearchUtils.cosineDistance(v0, v1), EPSILON);
    }

    @Test
    void cosineDistance_bothVectorsAreZero_returnsOne() {
        // Current implementation returns 1.0 if any norm is 0.
        // If both are zero, they could be considered identical (distance 0),
        // but similarity is undefined. The 1.0 indicates no defined similarity.
        List<Double> v0_1 = Arrays.asList(0.0, 0.0, 0.0);
        List<Double> v0_2 = Arrays.asList(0.0, 0.0, 0.0);
        assertEquals(1.0, SimilaritySearchUtils.cosineDistance(v0_1, v0_2), EPSILON);
    }


    @Test
    void cosineDistance_mismatchedLengths_throwsVectorStoreException() {
        List<Double> v1 = Arrays.asList(1.0, 2.0);
        List<Double> v2 = Arrays.asList(1.0, 2.0, 3.0);
        VectorStoreException ex = assertThrows(VectorStoreException.class, () -> {
            SimilaritySearchUtils.cosineDistance(v1, v2);
        });
        assertTrue(ex.getMessage().contains("Vectors must have the same length"));
    }

    @Test
    void cosineDistance_emptyVectors_throwsVectorStoreException() {
        List<Double> v1 = Collections.emptyList();
        List<Double> v2 = Collections.emptyList();
        VectorStoreException ex = assertThrows(VectorStoreException.class, () -> {
            SimilaritySearchUtils.cosineDistance(v1, v2);
        });
        assertTrue(ex.getMessage().contains("Vectors cannot be empty"));
    }

    @Test
    void cosineDistance_nullVector1_throwsNullPointerException() {
        List<Double> v2 = Arrays.asList(1.0, 2.0, 3.0);
        assertThrows(NullPointerException.class, () -> {
            SimilaritySearchUtils.cosineDistance(null, v2);
        });
    }

    @Test
    void cosineDistance_nullVector2_throwsNullPointerException() {
        List<Double> v1 = Arrays.asList(1.0, 2.0, 3.0);
        assertThrows(NullPointerException.class, () -> {
            SimilaritySearchUtils.cosineDistance(v1, null);
        });
    }

    @Test
    void cosineDistance_handlesFloatingPointInaccuraciesForNearIdentical() {
        // Slightly perturbed vector
        List<Double> v1 = Arrays.asList(1.0, 2.0, 3.0);
        List<Double> v2 = Arrays.asList(1.0000001, 2.0000001, 3.0000001);
        double distance = SimilaritySearchUtils.cosineDistance(v1, v2);
        assertTrue(distance >= 0 && distance < EPSILON, "Distance should be very close to 0: " + distance);
    }

    @Test
    void cosineDistance_handlesFloatingPointInaccuraciesForNearOpposite() {
        List<Double> v1 = Arrays.asList(1.0, 2.0, 3.0);
        List<Double> v2 = Arrays.asList(-1.0000001, -2.0000001, -3.0000001);
        double distance = SimilaritySearchUtils.cosineDistance(v1, v2);
        assertTrue(distance > (2.0 - EPSILON) && distance <= 2.0, "Distance should be very close to 2: " + distance);
    }
}

package com.skanga.rag.vectorstore;

import com.skanga.rag.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MemoryVectorStoreTests {

    private MemoryVectorStore vectorStore;
    private Document doc1, doc2, doc3, doc4;

    @BeforeEach
    void setUp() {
        vectorStore = new MemoryVectorStore(3); // Default topK for store, not directly used by search k

        doc1 = new Document("Document about apples and oranges.");
        doc1.setId("doc1");
        doc1.setEmbedding(Arrays.asList(0.1, 0.2, 0.7));

        doc2 = new Document("Exploring bananas and cherries.");
        doc2.setId("doc2");
        doc2.setEmbedding(Arrays.asList(0.7, 0.2, 0.1));

        doc3 = new Document("More on apples and cherries."); // Similar to doc1 and doc2 in parts
        doc3.setId("doc3");
        doc3.setEmbedding(Arrays.asList(0.3, 0.3, 0.4));

        doc4 = new Document("Document without embedding initially.");
        doc4.setId("doc4");
    }

    @Test
    void addDocument_validDocument_addsToStore() {
        vectorStore.addDocument(doc1);
        assertEquals(1, vectorStore.getAllDocuments().size());
        assertTrue(vectorStore.getAllDocuments().contains(doc1));
    }

    @Test
    void addDocument_nullDocument_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> vectorStore.addDocument(null));
    }

    @Test
    void addDocument_documentWithoutEmbedding_throwsVectorStoreException() {
        Exception ex = assertThrows(VectorStoreException.class, () -> vectorStore.addDocument(doc4));
        assertTrue(ex.getMessage().contains("Document embedding cannot be null or empty"));
    }

    @Test
    void addDocument_documentWithEmptyEmbedding_throwsVectorStoreException() {
        doc4.setEmbedding(Collections.emptyList());
        Exception ex = assertThrows(VectorStoreException.class, () -> vectorStore.addDocument(doc4));
        assertTrue(ex.getMessage().contains("Document embedding cannot be null or empty"));
    }


    @Test
    void addDocuments_addsAllValidDocuments() {
        List<Document> docsToAdd = Arrays.asList(doc1, doc2);
        vectorStore.addDocuments(docsToAdd);
        assertEquals(2, vectorStore.getAllDocuments().size());
        assertTrue(vectorStore.getAllDocuments().contains(doc1));
        assertTrue(vectorStore.getAllDocuments().contains(doc2));
    }

    @Test
    void addDocuments_emptyList_doesNothing() {
        vectorStore.addDocuments(Collections.emptyList());
        assertTrue(vectorStore.getAllDocuments().isEmpty());
    }

    @Test
    void addDocuments_listWithNullDocument_throwsNullPointerException() {
        List<Document> docsWithNull = new ArrayList<>();
        docsWithNull.add(doc1);
        docsWithNull.add(null);
        assertThrows(NullPointerException.class, () -> vectorStore.addDocuments(docsWithNull));
    }

    @Test
    void addDocuments_listWithInvalidDocument_throwsVectorStoreException() {
        List<Document> docsWithInvalid = Arrays.asList(doc1, doc4); // doc4 has no embedding
        assertThrows(VectorStoreException.class, () -> vectorStore.addDocuments(docsWithInvalid));
        // Check that doc1 might have been added before failure, or none if transactional (current is not transactional)
        // Current addDocuments iterates and calls addDocument, so doc1 would be added.
        assertEquals(1, vectorStore.getAllDocuments().size());
        assertTrue(vectorStore.getAllDocuments().contains(doc1));
    }

    @Test
    void similaritySearch_returnsKNearestDocumentsSortedByScore() {
        vectorStore.addDocuments(Arrays.asList(doc1, doc2, doc3));
        List<Double> queryEmbedding = Arrays.asList(0.15, 0.25, 0.6); // Closer to doc1

        List<Document> results = vectorStore.similaritySearch(queryEmbedding, 2);

        assertEquals(2, results.size());
        // doc1 should be first (highest score/lowest distance)
        assertEquals("doc1", results.get(0).getId());
        // doc3 should be second, as it's generally closer than doc2 to the query
        assertEquals("doc3", results.get(1).getId());

        assertTrue(results.get(0).getScore() >= results.get(1).getScore(), "Results should be sorted by score descending.");
        // Scores are 1.0 - distance.
        // Distance to doc1: 1 - ( (0.15*0.1 + 0.25*0.2 + 0.6*0.7) / (sqrt(0.15^2+0.25^2+0.6^2) * sqrt(0.1^2+0.2^2+0.7^2)) )
        // = 1 - ( (0.015 + 0.05 + 0.42) / (sqrt(0.0225+0.0625+0.36) * sqrt(0.01+0.04+0.49)) )
        // = 1 - ( 0.485 / (sqrt(0.445) * sqrt(0.54)) )
        // = 1 - ( 0.485 / (0.667 * 0.735) ) = 1 - (0.485 / 0.490) approx = 1 - 0.989 = 0.011
        // Score for doc1 should be approx 0.989

        // Distance to doc3: 1 - ( (0.15*0.3 + 0.25*0.3 + 0.6*0.4) / (sqrt(0.445) * sqrt(0.09+0.09+0.16)) )
        // = 1 - ( (0.045 + 0.075 + 0.24) / (sqrt(0.445) * sqrt(0.34)) )
        // = 1 - ( 0.36 / (0.667 * 0.583) ) = 1 - (0.36 / 0.389) approx = 1 - 0.925 = 0.075
        // Score for doc3 should be approx 0.925

        assertEquals(0.989f, results.get(0).getScore(), 0.01f);
        assertEquals(0.925f, results.get(1).getScore(), 0.01f);

    }

    @Test
    void similaritySearch_kIsLargerThanStoredDocuments_returnsAllStoredDocuments() {
        vectorStore.addDocuments(Arrays.asList(doc1, doc2));
        List<Double> queryEmbedding = Arrays.asList(0.1, 0.1, 0.1);
        List<Document> results = vectorStore.similaritySearch(queryEmbedding, 5); // k=5, but only 2 docs

        assertEquals(2, results.size());
    }

    @Test
    void similaritySearch_emptyStore_returnsEmptyList() {
        List<Double> queryEmbedding = Arrays.asList(0.1, 0.1, 0.1);
        List<Document> results = vectorStore.similaritySearch(queryEmbedding, 3);
        assertTrue(results.isEmpty());
    }

    @Test
    void similaritySearch_invalidK_throwsIllegalArgumentException() {
        List<Double> queryEmbedding = Arrays.asList(0.1, 0.1, 0.1);
        assertThrows(IllegalArgumentException.class, () -> vectorStore.similaritySearch(queryEmbedding, 0));
        assertThrows(IllegalArgumentException.class, () -> vectorStore.similaritySearch(queryEmbedding, -1));
    }

    @Test
    void similaritySearch_nullQueryEmbedding_throwsVectorStoreException() {
        assertThrows(NullPointerException.class, () -> vectorStore.similaritySearch(null, 3));
    }

    @Test
    void similaritySearch_documentWithMismatchedEmbeddingDimension_isSkippedOrErrors() {
        // This depends on SimilaritySearchUtils.cosineDistance behavior
        // which throws VectorStoreException for mismatched dimensions.
        // MemoryVectorStore catches this and prints to stderr, skipping the doc.
        vectorStore.addDocument(doc1); // doc1 embedding size 3
        Document mismatchedDoc = new Document("Mismatched dim");
        mismatchedDoc.setEmbedding(Arrays.asList(0.1, 0.2)); // size 2
        vectorStore.addDocument(mismatchedDoc); // This will be skipped in search

        List<Double> queryEmbedding = Arrays.asList(0.1, 0.2, 0.3);
        List<Document> results = vectorStore.similaritySearch(queryEmbedding, 2);

        assertEquals(1, results.size()); // Only doc1 should be found
        assertEquals(doc1.getId(), results.get(0).getId());
    }

    @Test
    void clear_emptiesTheStore() {
        vectorStore.addDocuments(Arrays.asList(doc1, doc2));
        assertFalse(vectorStore.getAllDocuments().isEmpty());
        vectorStore.clear();
        assertTrue(vectorStore.getAllDocuments().isEmpty());
    }
}

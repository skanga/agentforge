package com.skanga.rag.postprocessing;

import com.skanga.chat.messages.Message;
import com.skanga.chat.enums.MessageRole;
import com.skanga.rag.Document;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveThresholdPostProcessorTests {

    private final Message dummyQuestion = new Message(MessageRole.USER, "dummy question");

    @Test
    void process_filtersBasedOnMeanAndStdDev() {
        // Scores: 0.9, 0.8, 0.7, 0.3, 0.2
        // Mean = (0.9+0.8+0.7+0.3+0.2)/5 = 2.9/5 = 0.58
        // Differences from mean: 0.32, 0.22, 0.12, -0.28, -0.38
        // Squared differences: 0.1024, 0.0484, 0.0144, 0.0784, 0.1444
        // Sum of squared diff: 0.388
        // Variance = 0.388 / 5 = 0.0776
        // StdDev = sqrt(0.0776) approx 0.2785
        // factor = 1.0, minDocs = 0
        // Threshold = mean - 1.0 * stdDev = 0.58 - 0.2785 = 0.3015
        // Expected to keep: 0.9, 0.8, 0.7
        AdaptiveThresholdPostProcessor processor = new AdaptiveThresholdPostProcessor(1.0, 0);

        Document doc1 = new Document("D1"); doc1.setScore(0.9f);
        Document doc2 = new Document("D2"); doc2.setScore(0.8f);
        Document doc3 = new Document("D3"); doc3.setScore(0.7f);
        Document doc4 = new Document("D4"); doc4.setScore(0.3f); // Should be filtered out
        Document doc5 = new Document("D5"); doc5.setScore(0.2f); // Should be filtered out
        List<Document> documents = Arrays.asList(doc1, doc2, doc3, doc4, doc5);

        List<Document> processed = processor.process(dummyQuestion, documents);

        assertEquals(3, processed.size());
        assertTrue(processed.contains(doc1));
        assertTrue(processed.contains(doc2));
        assertTrue(processed.contains(doc3));
        assertFalse(processed.contains(doc4));
        assertFalse(processed.contains(doc5));
    }

    @Test
    void process_minDocsEnsuresMinimumReturned() {
        // Same stats as above: mean=0.58, stdDev=0.2785, factor=1.0 => threshold=0.3015
        // Filtered would be 3 docs (0.9, 0.8, 0.7)
        // If minDocs = 4, it should return top 4 original docs.
        AdaptiveThresholdPostProcessor processor = new AdaptiveThresholdPostProcessor(1.0, 4);

        Document doc1 = new Document("D1"); doc1.setScore(0.9f);
        Document doc2 = new Document("D2"); doc2.setScore(0.8f);
        Document doc3 = new Document("D3"); doc3.setScore(0.7f);
        Document doc4 = new Document("D4"); doc4.setScore(0.3f); // Below threshold, but kept by minDocs
        Document doc5 = new Document("D5"); doc5.setScore(0.2f); // Should be dropped

        List<Document> documents = Arrays.asList(doc1, doc2, doc3, doc4, doc5);
        List<Document> processed = processor.process(dummyQuestion, documents);

        assertEquals(4, processed.size());
        assertTrue(processed.contains(doc1));
        assertTrue(processed.contains(doc2));
        assertTrue(processed.contains(doc3));
        assertTrue(processed.contains(doc4)); // Included due to minDocs
        assertFalse(processed.contains(doc5));
        // Check order (should be sorted by score descending)
        assertEquals(doc1, processed.get(0));
        assertEquals(doc2, processed.get(1));
        assertEquals(doc3, processed.get(2));
        assertEquals(doc4, processed.get(3));
    }

    @Test
    void process_minDocsGreaterThanAvailableDocs_returnsAllSorted() {
        AdaptiveThresholdPostProcessor processor = new AdaptiveThresholdPostProcessor(1.0, 5); // minDocs 5
        Document doc1 = new Document("D1"); doc1.setScore(0.7f);
        Document doc2 = new Document("D2"); doc2.setScore(0.9f); // Higher score
        Document doc3 = new Document("D3"); doc3.setScore(0.8f);
        List<Document> documents = Arrays.asList(doc1, doc2, doc3); // Only 3 docs available

        List<Document> processed = processor.process(dummyQuestion, documents);
        assertEquals(3, processed.size());
        // Should be sorted by score
        assertEquals(doc2, processed.get(0)); // 0.9
        assertEquals(doc3, processed.get(1)); // 0.8
        assertEquals(doc1, processed.get(2)); // 0.7
    }


    @Test
    void process_emptyDocumentList_returnsEmptyList() {
        AdaptiveThresholdPostProcessor processor = new AdaptiveThresholdPostProcessor(1.0, 2);
        List<Document> processed = processor.process(dummyQuestion, Collections.emptyList());
        assertTrue(processed.isEmpty());
    }

    @Test
    void process_singleDocument_returnsSingleDocumentIfMinDocsAllows() {
        AdaptiveThresholdPostProcessor processorMin1 = new AdaptiveThresholdPostProcessor(1.0, 1);
        AdaptiveThresholdPostProcessor processorMin0 = new AdaptiveThresholdPostProcessor(1.0, 0);
        Document doc1 = new Document("D1"); doc1.setScore(0.5f);
        List<Document> documents = Collections.singletonList(doc1);

        List<Document> processed1 = processorMin1.process(dummyQuestion, documents);
        assertEquals(1, processed1.size());
        assertTrue(processed1.contains(doc1));

        // If minDocs is 0, and only 1 doc, stdDev is 0, threshold = mean = score. So it passes.
        List<Document> processed0 = processorMin0.process(dummyQuestion, documents);
        assertEquals(1, processed0.size());
        assertTrue(processed0.contains(doc1));
    }

    @Test
    void process_allScoresIdentical_stdDevIsZero_thresholdIsMean() {
        // If all scores are same, std dev = 0. Threshold = mean. All docs pass.
        AdaptiveThresholdPostProcessor processor = new AdaptiveThresholdPostProcessor(1.0, 0);
        Document doc1 = new Document("D1"); doc1.setScore(0.8f);
        Document doc2 = new Document("D2"); doc2.setScore(0.8f);
        Document doc3 = new Document("D3"); doc3.setScore(0.8f);
        List<Document> documents = Arrays.asList(doc1, doc2, doc3);

        List<Document> processed = processor.process(dummyQuestion, documents);
        assertEquals(3, processed.size());
    }

    @Test
    void constructor_negativeMinDocs_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new AdaptiveThresholdPostProcessor(1.0, -1));
    }
}

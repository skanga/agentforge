package com.skanga.rag.postprocessing;

import com.skanga.chat.messages.Message;
import com.skanga.chat.enums.MessageRole;
import com.skanga.rag.Document;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FixedThresholdPostProcessorTests {

    private final Message dummyQuestion = new Message(MessageRole.USER, "dummy question");

    @Test
    void process_filtersDocumentsBelowThreshold() {
        FixedThresholdPostProcessor processor = new FixedThresholdPostProcessor(0.7f);
        Document doc1 = new Document("Doc 1"); doc1.setScore(0.9f);
        Document doc2 = new Document("Doc 2"); doc2.setScore(0.6f);
        Document doc3 = new Document("Doc 3"); doc3.setScore(0.75f);
        Document doc4 = new Document("Doc 4"); doc4.setScore(0.5f);

        List<Document> documents = Arrays.asList(doc1, doc2, doc3, doc4);
        List<Document> processed = processor.process(dummyQuestion, documents);

        assertEquals(2, processed.size());
        assertTrue(processed.contains(doc1));
        assertTrue(processed.contains(doc3));
        assertFalse(processed.contains(doc2));
        assertFalse(processed.contains(doc4));
    }

    @Test
    void process_thresholdAllowsAllDocuments() {
        FixedThresholdPostProcessor processor = new FixedThresholdPostProcessor(0.1f);
        Document doc1 = new Document("Doc 1"); doc1.setScore(0.2f);
        Document doc2 = new Document("Doc 2"); doc2.setScore(0.3f);
        List<Document> documents = Arrays.asList(doc1, doc2);

        List<Document> processed = processor.process(dummyQuestion, documents);
        assertEquals(2, processed.size());
        assertTrue(processed.containsAll(documents));
    }

    @Test
    void process_thresholdRejectsAllDocuments() {
        FixedThresholdPostProcessor processor = new FixedThresholdPostProcessor(0.9f);
        Document doc1 = new Document("Doc 1"); doc1.setScore(0.2f);
        Document doc2 = new Document("Doc 2"); doc2.setScore(0.3f);
        List<Document> documents = Arrays.asList(doc1, doc2);

        List<Document> processed = processor.process(dummyQuestion, documents);
        assertTrue(processed.isEmpty());
    }

    @Test
    void process_emptyDocumentList_returnsEmptyList() {
        FixedThresholdPostProcessor processor = new FixedThresholdPostProcessor(0.5f);
        List<Document> processed = processor.process(dummyQuestion, Collections.emptyList());
        assertTrue(processed.isEmpty());
    }

    @Test
    void process_documentsWithScoresEqualToThreshold_areKept() {
        FixedThresholdPostProcessor processor = new FixedThresholdPostProcessor(0.5f);
        Document doc1 = new Document("Doc 1"); doc1.setScore(0.5f);
        Document doc2 = new Document("Doc 2"); doc2.setScore(0.50001f);
        Document doc3 = new Document("Doc 3"); doc3.setScore(0.49999f);
        List<Document> documents = Arrays.asList(doc1, doc2, doc3);

        List<Document> processed = processor.process(dummyQuestion, documents);
        assertEquals(2, processed.size());
        assertTrue(processed.contains(doc1));
        assertTrue(processed.contains(doc2));
        assertFalse(processed.contains(doc3));
    }

    @Test
    void constructor_nullQuestion_processStillWorksIfQuestionNotUsed() {
        // The FixedThresholdPostProcessor does not use the 'question' parameter.
        FixedThresholdPostProcessor processor = new FixedThresholdPostProcessor(0.5f);
        Document doc1 = new Document("Doc 1"); doc1.setScore(0.6f);
        List<Document> documents = Collections.singletonList(doc1);

        List<Document> processed = processor.process(null, documents); // Pass null question
        assertEquals(1, processed.size());
    }

    @Test
    void constructor_nullDocuments_throwsNullPointerException() {
        FixedThresholdPostProcessor processor = new FixedThresholdPostProcessor(0.5f);
        assertThrows(NullPointerException.class, () -> {
            processor.process(dummyQuestion, null);
        });
    }
}

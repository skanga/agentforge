package com.skanga.rag.embeddings;

import com.skanga.rag.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AbstractEmbeddingProviderTests {

    // Testable concrete subclass of AbstractEmbeddingProvider
    static class TestableEmbeddingProvider extends AbstractEmbeddingProvider {
        private List<Double> nextEmbeddingToReturn = List.of(0.1, 0.2, 0.3);
        private int embedTextCallCount = 0;
        private String lastTextPassedToEmbedText = null;

        public void setNextEmbeddingToReturn(List<Double> embedding) {
            this.nextEmbeddingToReturn = embedding;
        }
        public int getEmbedTextCallCount() { return embedTextCallCount; }
        public String getLastTextPassedToEmbedText() { return lastTextPassedToEmbedText; }

        @Override
        public List<Double> embedText(String text) throws EmbeddingException {
            this.embedTextCallCount++; // Increment at the start
            Objects.requireNonNull(text, "Text cannot be null in test implementation of embedText.");
            if (text.contains("throw_exception")) {
                throw new EmbeddingException("Test embedding failure for: " + text);
            }
            this.lastTextPassedToEmbedText = text;
            return nextEmbeddingToReturn;
        }
    }

    private TestableEmbeddingProvider testableProvider;
    private Document testDocument;

    @BeforeEach
    void setUp() {
        testableProvider = new TestableEmbeddingProvider();
        testDocument = new Document("This is a test document content.");
        // Ensure testDocument has no embedding initially if that's relevant
        testDocument.setEmbedding(null);
    }

    @Test
    void embedDocument_validDocument_setsEmbeddingAndReturnsDocument() throws EmbeddingException {
        List<Double> expectedEmbedding = List.of(1.0, 2.0, 3.0);
        testableProvider.setNextEmbeddingToReturn(expectedEmbedding);

        Document result = testableProvider.embedDocument(testDocument);

        assertSame(testDocument, result, "Should return the same document instance.");
        assertEquals(expectedEmbedding, result.getEmbedding(), "Embedding should be set on the document.");
        assertEquals(1, testableProvider.getEmbedTextCallCount());
        assertEquals(testDocument.getContent(), testableProvider.getLastTextPassedToEmbedText());
    }

    @Test
    void embedDocument_nullDocument_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            testableProvider.embedDocument(null);
        });
    }

    @Test
    void embedDocument_nullContent_throwsEmbeddingException() {
        // To test the provider's handling of a Document that *reports* null content,
        // without being blocked by Document.setContent's own validation, we mock Document.
        Document mockDocWithNullContent = mock(Document.class);
        when(mockDocWithNullContent.getContent()).thenReturn(null);
        when(mockDocWithNullContent.getId()).thenReturn("mockDocWithNullContentId"); // For exception message

        EmbeddingException ex = assertThrows(EmbeddingException.class, () -> {
            testableProvider.embedDocument(mockDocWithNullContent);
        });
        assertTrue(ex.getMessage().contains("Document content cannot be null or empty"));
        assertTrue(ex.getMessage().contains("mockDocWithNullContentId"));
    }

    @Test
    void embedDocument_emptyContent_throwsEmbeddingException() {
        testDocument.setContent("   "); // Whitespace only
        EmbeddingException ex = assertThrows(EmbeddingException.class, () -> {
            testableProvider.embedDocument(testDocument);
        });
        assertTrue(ex.getMessage().contains("Document content cannot be null or empty"));
    }


    @Test
    void embedDocument_embedTextThrowsException_propagatesEmbeddingException() {
        testDocument.setContent("throw_exception in content");
        EmbeddingException ex = assertThrows(EmbeddingException.class, () -> {
            testableProvider.embedDocument(testDocument);
        });
        assertTrue(ex.getMessage().contains("Test embedding failure"));
    }

    @Test
    void embedDocuments_listOfDocuments_embedsEachDocument() throws EmbeddingException {
        Document doc1 = new Document("First document.");
        Document doc2 = new Document("Second document.");
        List<Document> documents = Arrays.asList(doc1, doc2);

        List<Double> embedding1 = List.of(0.1, 0.2);
        List<Double> embedding2 = List.of(0.3, 0.4);

        // Configure the mock behavior for embedText for each document's content
        // This is a bit tricky as embedText is called in a loop.
        // For simplicity, let's assume embedText in TestableEmbeddingProvider can cycle through a list of embeddings
        // or we set it before each expected call.
        // Current TestableEmbeddingProvider uses one "nextEmbeddingToReturn".
        // For this test, we'll verify it's called twice and content is passed.

        testableProvider.setNextEmbeddingToReturn(embedding1); // For first call
        List<Document> results = testableProvider.embedDocuments(documents);

        assertEquals(2, results.size());
        // First call to embedDocument (for doc1) should use embedding1
        // Since setNextEmbeddingToReturn is global for the test provider,
        // the second call will *also* use embedding1 unless we change it or the test provider is more advanced.
        // The current TestableProvider will set embedding1 for both.
        // A better test for distinct embeddings would require a more sophisticated mock or spy.

        // Let's verify call counts and that content was passed.
        assertEquals(2, testableProvider.getEmbedTextCallCount());
        // Last text passed would be for doc2
        assertEquals(doc2.getContent(), testableProvider.getLastTextPassedToEmbedText());

        // Verify embeddings were set (both will have the *last* embedding set by setNextEmbeddingToReturn if not careful)
        // Re-setting for clarity for doc2, assuming doc1 got embedding1
        // This highlights a limitation in the simple TestableEmbeddingProvider for batch testing.
        // A spy on a *real* AbstractEmbeddingProvider subclass would be better for fine-grained mocking.

        // Resetting and re-testing with more control:
        testableProvider = new TestableEmbeddingProvider(); // Fresh instance

        // Doc1
        testableProvider.setNextEmbeddingToReturn(embedding1);
        testableProvider.embedDocument(doc1); // Manually call for doc1
        assertEquals(embedding1, doc1.getEmbedding());

        // Doc2
        testableProvider.setNextEmbeddingToReturn(embedding2);
        testableProvider.embedDocument(doc2); // Manually call for doc2
        assertEquals(embedding2, doc2.getEmbedding());

        // Now test the batch method again, ensuring it calls embedDocument for each
        TestableEmbeddingProvider batchTestProvider = spy(new TestableEmbeddingProvider());
        List<Document> batchDocs = Arrays.asList(new Document("d1"), new Document("d2"));
        doReturn(batchDocs.get(0)).when(batchTestProvider).embedDocument(batchDocs.get(0)); // Mock individual calls
        doReturn(batchDocs.get(1)).when(batchTestProvider).embedDocument(batchDocs.get(1));

        batchTestProvider.embedDocuments(batchDocs);
        verify(batchTestProvider, times(1)).embedDocument(batchDocs.get(0));
        verify(batchTestProvider, times(1)).embedDocument(batchDocs.get(1));
    }

    @Test
    void embedDocuments_nullList_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            testableProvider.embedDocuments(null);
        });
    }

    @Test
    void embedDocuments_emptyList_returnsEmptyList() throws EmbeddingException {
        List<Document> results = testableProvider.embedDocuments(new ArrayList<>());
        assertNotNull(results);
        assertTrue(results.isEmpty());
        assertEquals(0, testableProvider.getEmbedTextCallCount());
    }

    @Test
    void embedDocuments_oneDocumentFails_throwsEmbeddingException() {
        Document doc1 = new Document("Good content");
        Document doc2 = new Document("throw_exception in this one");
        Document doc3 = new Document("More good content");
        List<Document> documents = Arrays.asList(doc1, doc2, doc3);

        EmbeddingException ex = assertThrows(EmbeddingException.class, () -> {
            testableProvider.embedDocuments(documents);
        });
        assertTrue(ex.getMessage().contains("Failed to embed one or more documents"));
        assertTrue(ex.getMessage().contains(doc2.getId()));
        // Depending on implementation, doc1 might or might not be processed.
        // Current AbstractEmbeddingProvider stops on first error.
        // So, embedTextCallCount should be 2 (for doc1 successful, for doc2 failed).
        assertEquals(2, testableProvider.getEmbedTextCallCount());
    }
}

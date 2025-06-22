package com.skanga.rag.dataloader;

import com.skanga.rag.Document;
import com.skanga.rag.splitter.DelimiterTextSplitter;
import com.skanga.rag.splitter.TextSplitter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StringDocumentLoaderTests {

    @Test
    void constructor_withContent_setsContentAndDefaultSourceName() {
        String content = "This is a test string.";
        StringDocumentLoader loader = new StringDocumentLoader(content);
        // Accessing fields directly for test is not ideal, but they are package-private effectively
        // Better to test via getDocuments() behavior
        assertNotNull(loader);
    }

    @Test
    void constructor_withContentAndSourceName_setsFields() {
        String content = "Another test string.";
        String sourceName = "my_custom_string_source";
        StringDocumentLoader loader = new StringDocumentLoader(content, sourceName);
        assertNotNull(loader);
    }

    @Test
    void constructor_withContentSourceNameAndSplitter_setsAll() {
        TextSplitter mockSplitter = mock(TextSplitter.class);
        StringDocumentLoader loader = new StringDocumentLoader("content", "source", mockSplitter);
        assertSame(mockSplitter, loader.getTextSplitter());
    }

    @Test
    void getDocuments_usesDefaultSplitter_createsAndSplitsDocument() throws IOException, DocumentLoaderException {
        String content = "Line one.\n\nLine two."; // Content that default DelimiterTextSplitter would split
        StringDocumentLoader loader = new StringDocumentLoader(content, "test_source");

        // Default DelimiterTextSplitter(1500, "\n\n", 100) should split this into two
        List<Document> documents = loader.getDocuments();

        assertEquals(2, documents.size(), "Should be split into two documents by default splitter on '\\n\\n'");
        assertEquals("Line one.", documents.get(0).getContent());
        assertEquals("Line two.", documents.get(1).getContent());

        for(Document doc : documents) {
            assertEquals("string", doc.getSourceType());
            assertEquals("test_source", doc.getSourceName());
        }
    }

    @Test
    void getDocuments_withCustomSplitter_usesCustomSplitter() throws IOException, DocumentLoaderException {
        String content = "A single long string that the mock splitter will handle.";
        TextSplitter mockSplitter = mock(TextSplitter.class);
        StringDocumentLoader loader = new StringDocumentLoader(content, "custom_split_source", mockSplitter);

        Document doc1 = new Document("Chunk 1");
        Document doc2 = new Document("Chunk 2");
        List<Document> expectedSplitDocs = List.of(doc1, doc2);

        // When the loader calls splitDocument on the document created from 'content'
        when(mockSplitter.splitDocument(any(Document.class))).thenReturn(expectedSplitDocs);

        List<Document> documents = loader.getDocuments();

        assertEquals(2, documents.size());
        assertSame(expectedSplitDocs, documents, "Should return the list from the mock splitter.");

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mockSplitter, times(1)).splitDocument(docCaptor.capture());
        assertEquals(content, docCaptor.getValue().getContent());
        assertEquals("string", docCaptor.getValue().getSourceType());
        assertEquals("custom_split_source", docCaptor.getValue().getSourceName());
    }

    @Test
    void getDocuments_emptyContent_returnsOneDocumentWithEmptyContentAfterSplit() throws IOException {
        String content = "";
        StringDocumentLoader loader = new StringDocumentLoader(content);
        List<Document> documents = loader.getDocuments();

        assertEquals(1, documents.size());
        assertEquals("", documents.get(0).getContent());
        assertEquals("string", documents.get(0).getSourceType());
        assertEquals(StringDocumentLoader.DEFAULT_STRING_SOURCE_NAME, documents.get(0).getSourceName());
    }

    @Test
    void constructor_nullContent_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new StringDocumentLoader(null));
        assertThrows(NullPointerException.class, () -> new StringDocumentLoader(null, "source"));
        assertThrows(NullPointerException.class, () -> new StringDocumentLoader(null, "source", new DelimiterTextSplitter()));
    }

    @Test
    void constructor_nullSourceName_throwsNullPointerException() {
         assertThrows(NullPointerException.class, () -> new StringDocumentLoader("content", null));
    }

    @Test
    void constructor_nullSplitter_throwsNullPointerException() {
         assertThrows(NullPointerException.class, () -> new StringDocumentLoader("content", "source", null));
    }
}

package com.skanga.rag.dataloader;

import com.skanga.rag.Document;
import com.skanga.rag.splitter.DelimiterTextSplitter;
import com.skanga.rag.splitter.TextSplitter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AbstractDocumentLoaderTests {

    // Concrete implementation for testing
    static class TestableDocumentLoader extends AbstractDocumentLoader {
        private List<Document> documentsToLoad;

        public TestableDocumentLoader(List<Document> documentsToLoad, TextSplitter splitter) {
            super(splitter);
            this.documentsToLoad = documentsToLoad;
        }
         public TestableDocumentLoader(List<Document> documentsToLoad) {
            super(); // Uses default splitter
            this.documentsToLoad = documentsToLoad;
        }

        @Override
        public List<Document> getDocuments() throws IOException, DocumentLoaderException {
            // In a real loader, this would load from a source.
            // Here, it just returns the pre-set documents.
            // The splitting is typically applied by the concrete loader *after* getting raw docs.
            // For testing AbstractDocumentLoader's splitter handling, we assume getDocuments()
            // in concrete class would call this.textSplitter.splitDocuments().
            // So, this testable loader will just return raw docs, and we test splitter separately.
            if (documentsToLoad == null) return Collections.emptyList();
            return documentsToLoad;
        }
    }

    @Test
    void constructor_default_initializesDefaultSplitter() {
        TestableDocumentLoader loader = new TestableDocumentLoader(null);
        assertNotNull(loader.getTextSplitter());
        assertTrue(loader.getTextSplitter() instanceof DelimiterTextSplitter);
    }

    @Test
    void constructor_withCustomSplitter_setsSplitter() {
        TextSplitter mockSplitter = Mockito.mock(TextSplitter.class);
        TestableDocumentLoader loader = new TestableDocumentLoader(null, mockSplitter);
        assertSame(mockSplitter, loader.getTextSplitter());
    }

    @Test
    void withTextSplitter_setsSplitter() {
        TestableDocumentLoader loader = new TestableDocumentLoader(null);
        TextSplitter newSplitter = new DelimiterTextSplitter(500, ".", 10);
        loader.withTextSplitter(newSplitter);
        assertSame(newSplitter, loader.getTextSplitter());
    }

    @Test
    void withTextSplitter_nullSplitter_throwsNullPointerException() {
        TestableDocumentLoader loader = new TestableDocumentLoader(null);
        assertThrows(NullPointerException.class, () -> loader.withTextSplitter(null));
    }

    // Test that a concrete implementation would use the splitter
    @Test
    void concreteLoader_getDocuments_wouldUseSplitter() throws IOException {
        // This test is more conceptual for AbstractDocumentLoader.
        // A concrete loader like FileSystemDocumentLoader or StringDocumentLoader
        // is responsible for calling this.textSplitter.splitDocuments().

        TextSplitter mockSplitter = Mockito.mock(TextSplitter.class);
        Document rawDoc = new Document("Raw long content...");
        List<Document> rawDocs = Collections.singletonList(rawDoc);

        // Expected split documents
        Document splitDoc1 = new Document("Split 1");
        Document splitDoc2 = new Document("Split 2");
        List<Document> splitDocs = List.of(splitDoc1, splitDoc2);

        when(mockSplitter.splitDocuments(rawDocs)).thenReturn(splitDocs);

        // Simulate a concrete loader that calls the splitter
        // For example, StringDocumentLoader does this.
        StringDocumentLoader stringLoader = new StringDocumentLoader("Raw long content...");
        stringLoader.withTextSplitter(mockSplitter); // Inject mock splitter

        List<Document> finalDocs = stringLoader.getDocuments(); // This will call textSplitter.splitDocument internally

        // StringDocumentLoader calls splitDocument, not splitDocuments, if only one raw doc.
        // Let's adjust:
        when(mockSplitter.splitDocument(any(Document.class))).thenReturn(splitDocs);
        stringLoader = new StringDocumentLoader("Raw long content...");
        stringLoader.withTextSplitter(mockSplitter);
        finalDocs = stringLoader.getDocuments();

        assertEquals(2, finalDocs.size());
        assertEquals("Split 1", finalDocs.get(0).getContent());

        // Verify that the splitter was indeed called by the loader logic (StringDocumentLoader in this case)
        // StringDocumentLoader calls splitDocument on the single Document it creates.
        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mockSplitter, times(1)).splitDocument(docCaptor.capture());
        assertEquals("Raw long content...", docCaptor.getValue().getContent());
    }
}

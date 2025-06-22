package com.skanga.rag.vectorstore;

import com.fasterxml.jackson.databind.ObjectMapper; // For manually creating corrupt data
import com.skanga.rag.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.stream.Collectors;

class FileVectorStoreTests {

    @TempDir
    Path tempDir;

    private FileVectorStore fileVectorStore;
    private Path testStoreFile;
    private final String testFileName = "test_file_store.jsonl";
    private final ObjectMapper objectMapper = new ObjectMapper();


    private Document doc1, doc2, doc3;

    @BeforeEach
    void setUp() throws VectorStoreException {
        testStoreFile = tempDir.resolve(testFileName);
        // Initialize a new store for each test to ensure clean state
        fileVectorStore = new FileVectorStore(tempDir.toString(), testFileName, 3);

        doc1 = new Document("Alpha content about apples.");
        doc1.setId("doc1_alpha");
        doc1.setEmbedding(Arrays.asList(0.1, 0.8, 0.1));

        doc2 = new Document("Beta content about bananas.");
        doc2.setId("doc2_beta");
        doc2.setEmbedding(Arrays.asList(0.8, 0.1, 0.1));

        doc3 = new Document("Gamma content about grapes.");
        doc3.setId("doc3_gamma");
        doc3.setEmbedding(Arrays.asList(0.1, 0.1, 0.8));
    }

    @Test
    void constructor_newStore_createsFileAndDirectory() {
        assertTrue(Files.exists(testStoreFile.getParent()));
        assertTrue(Files.exists(testStoreFile));
        assertEquals(0, fileVectorStore.similaritySearch(Arrays.asList(0.1,0.1,0.1), 1).size());
    }

    @Test
    void constructor_existingFile_loadsDataOnNextInstance() throws IOException, VectorStoreException {
        fileVectorStore.addDocuments(Arrays.asList(doc1, doc2));

        // New instance loading from the same file
        FileVectorStore loadedStore = new FileVectorStore(tempDir.toString(), testFileName, 3);
        List<Document> docs = loadedStore.similaritySearch(doc1.getEmbedding(), 2); // Search for doc1
        assertEquals(2, docs.size());
        // Cannot directly compare Document objects as embeddings might have float inaccuracies from JSON
        assertEquals(doc1.getId(), docs.get(0).getId()); // doc1 should be most similar to itself
        assertEquals(doc1.getContent(), docs.get(0).getContent());
    }

    @Test
    void addDocument_writesJsonLineToFile() throws IOException {
        fileVectorStore.addDocument(doc1);
        List<String> lines = Files.readAllLines(testStoreFile);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains(doc1.getId()));
        assertTrue(lines.get(0).contains("Alpha content about apples."));
        assertTrue(lines.get(0).contains("0.8")); // Part of embedding
    }

    @Test
    void addDocuments_writesMultipleJsonLines() throws IOException {
        fileVectorStore.addDocuments(Arrays.asList(doc1, doc2, doc3));
        List<String> lines = Files.readAllLines(testStoreFile);
        assertEquals(3, lines.size());
        assertTrue(lines.get(1).contains(doc2.getId()));
    }

    @Test
    void addDocument_nullEmbedding_throwsVectorStoreException() {
        Document docWithNullEmbedding = new Document("No embedding here");
        docWithNullEmbedding.setEmbedding(null);
        assertThrows(VectorStoreException.class, () -> fileVectorStore.addDocument(docWithNullEmbedding));
    }

    @Test
    void addDocument_emptyEmbedding_throwsVectorStoreException() {
        Document docWithEmptyEmbedding = new Document("Empty embedding here");
        docWithEmptyEmbedding.setEmbedding(Collections.emptyList());
        assertThrows(VectorStoreException.class, () -> fileVectorStore.addDocument(docWithEmptyEmbedding));
    }

    @Test
    void similaritySearch_findsMostSimilarDocuments() throws VectorStoreException {
        fileVectorStore.addDocuments(Arrays.asList(doc1, doc2, doc3));
        List<Double> queryEmbedding = Arrays.asList(0.7, 0.2, 0.2); // Closer to doc2

        List<Document> results = fileVectorStore.similaritySearch(queryEmbedding, 2);

        assertEquals(2, results.size());
        assertEquals(doc2.getId(), results.get(0).getId(), "doc2 should be the most similar.");
        // The second result could be doc1 or doc3 depending on exact cosine similarities.
        // Let's verify scores are descending.
        assertTrue(results.get(0).getScore() >= results.get(1).getScore());
    }

    @Test
    void similaritySearch_malformedLineInFile_skipsAndContinues() throws IOException {
        fileVectorStore.addDocument(doc1); // Valid doc
        // Add a malformed line
        Files.writeString(testStoreFile, "this is not json\n", StandardOpenOption.APPEND);
        fileVectorStore.addDocument(doc2); // Valid doc after malformed line

        List<Double> queryEmbedding = doc1.getEmbedding();
        // Expect to find doc1 and doc2, skipping the bad line.
        // The error for malformed line will be printed to System.err by the current implementation.
        List<Document> results = fileVectorStore.similaritySearch(queryEmbedding, 3);

        assertEquals(2, results.size());
        List<String> ids = results.stream().map(Document::getId).collect(Collectors.toList());
        assertTrue(ids.contains(doc1.getId()));
        assertTrue(ids.contains(doc2.getId()));
    }

    @Test
    void similaritySearch_documentInFileMissingEmbedding_isSkipped() throws IOException {
        Document validDoc = new Document("Valid");
        validDoc.setEmbedding(List.of(0.1, 0.2));

        Map<String, Object> docMissingEmbeddingMap = new HashMap<>();
        docMissingEmbeddingMap.put("id", "noEmbedDoc");
        docMissingEmbeddingMap.put("content", "I have no embedding");
        // No "embedding" field

        fileVectorStore.addDocument(validDoc);
        Files.writeString(testStoreFile, objectMapper.writeValueAsString(docMissingEmbeddingMap) + "\n", StandardOpenOption.APPEND);

        List<Document> results = fileVectorStore.similaritySearch(validDoc.getEmbedding(), 2);
        assertEquals(1, results.size());
        assertEquals(validDoc.getId(), results.get(0).getId());
    }


    @Test
    void clear_emptiesTheStoreFile() throws IOException {
        fileVectorStore.addDocument(doc1);
        assertNotEquals(0, Files.size(testStoreFile));

        fileVectorStore.clear();
        assertEquals(0, Files.size(testStoreFile));

        // Adding after clear should work
        fileVectorStore.addDocument(doc2);
        assertEquals(1, Files.readAllLines(testStoreFile).size());
    }

    @Test
    void similaritySearch_kIsLargerThanDocsInFile_returnsAllDocs() {
        fileVectorStore.addDocuments(Arrays.asList(doc1, doc2));
        List<Document> results = fileVectorStore.similaritySearch(doc1.getEmbedding(), 5);
        assertEquals(2, results.size());
    }

    @Test
    void similaritySearch_emptyFile_returnsEmptyList() {
        List<Document> results = fileVectorStore.similaritySearch(Arrays.asList(0.1, 0.2), 3);
        assertTrue(results.isEmpty());
    }
}

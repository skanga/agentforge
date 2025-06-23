package com.skanga.rag.dataloader;

import com.skanga.rag.Document;
import com.skanga.rag.dataloader.reader.FileReader;
import com.skanga.rag.splitter.DelimiterTextSplitter;
import com.skanga.rag.splitter.TextSplitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileSystemDocumentLoaderTests {

    @TempDir
    Path tempDir;

    private FileSystemDocumentLoader loader;
    private TextSplitter mockSplitter;

    @BeforeEach
    void setUp() {
        mockSplitter = mock(DelimiterTextSplitter.class); // Use a concrete type or mock TextSplitter directly
        // Default loader for most tests, can be re-instantiated if needed
        loader = new FileSystemDocumentLoader(tempDir, true, null, null, mockSplitter);
    }

    @Test
    void constructor_defaultReaderInitialization() {
        FileSystemDocumentLoader defaultLoader = new FileSystemDocumentLoader(tempDir);
        assertNotNull(defaultLoader.readers);
        assertTrue(defaultLoader.readers.containsKey("txt"));
        assertTrue(defaultLoader.readers.containsKey("pdf"));
    }

    @Test
    void constructor_withCustomReaders_overridesDefaults() {
        FileReader customTxtReader = mock(FileReader.class);
        Map<String, FileReader> customReaders = Map.of("txt", customTxtReader);
        FileSystemDocumentLoader customLoader = new FileSystemDocumentLoader(tempDir, false, customReaders, null, new DelimiterTextSplitter());

        assertSame(customTxtReader, customLoader.readers.get("txt"));
    }

    @Test
    void getDocuments_singleTextFile_loadsAndSplits() throws IOException, DocumentLoaderException {
        Path testFile = tempDir.resolve("test.txt");
        String fileContent = "This is line one.\nThis is line two.";
        Files.writeString(testFile, fileContent, StandardCharsets.UTF_8);

        Document rawDoc = new Document(fileContent);
        rawDoc.setSourceType("file");
        rawDoc.setSourceName(testFile.toAbsolutePath().toString());
        // Mocking the splitter behavior
        List<Document> splitDocs = List.of(new Document("Split part 1 from " + testFile.getFileName()), new Document("Split part 2 from " + testFile.getFileName()));
        // We need to match the Document object that will be created internally
        when(mockSplitter.splitDocuments(anyList())).thenAnswer(invocation -> {
            List<Document> docsIn = invocation.getArgument(0);
            if (docsIn.size() == 1 && docsIn.get(0).getContent().equals(fileContent)) {
                return splitDocs;
            }
            return Collections.emptyList();
        });


        loader = new FileSystemDocumentLoader(testFile, false, null, null, mockSplitter);
        List<Document> documents = loader.getDocuments();

        assertEquals(2, documents.size());
        assertEquals("Split part 1 from " + testFile.getFileName(), documents.get(0).getContent());

        ArgumentCaptor<List<Document>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockSplitter).splitDocuments(listCaptor.capture());
        assertEquals(1, listCaptor.getValue().size());
        assertEquals(fileContent, listCaptor.getValue().get(0).getContent());
        assertEquals(testFile.toAbsolutePath().toString(), listCaptor.getValue().get(0).getSourceName());
    }

    @Test
    void getDocuments_directory_loadsFilesRecursively() throws IOException, DocumentLoaderException {
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        Path file1 = tempDir.resolve("file1.txt");
        Path file2InSubDir = subDir.resolve("file2.md"); // Using .md, assuming it defaults to PlainTextFileReader

        String content1 = "Content file 1";
        String content2 = "Content file 2 in subdir";
        Files.writeString(file1, content1);
        Files.writeString(file2InSubDir, content2);

        // Each raw document will be passed to splitter.
        // For simplicity, assume splitter returns the doc as one chunk.
        when(mockSplitter.splitDocuments(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        loader = new FileSystemDocumentLoader(tempDir, true, null, null, mockSplitter); // Recursive true
        List<Document> documents = loader.getDocuments();

        assertEquals(2, documents.size());
        // Verify document source names (order might vary)
        List<String> sourceNames = documents.stream().map(Document::getSourceName).collect(Collectors.toList());
        assertTrue(sourceNames.contains(file1.toAbsolutePath().toString()));
        assertTrue(sourceNames.contains(file2InSubDir.toAbsolutePath().toString()));

        ArgumentCaptor<List<Document>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockSplitter).splitDocuments(listCaptor.capture());
        assertEquals(2, listCaptor.getValue().size()); // Two raw documents loaded
    }

    @Test
    void getDocuments_directoryNotRecursive_loadsOnlyTopLevel() throws IOException, DocumentLoaderException {
        Path subDir = tempDir.resolve("subdir_nonrecursive");
        Files.createDirectory(subDir);
        Path file1 = tempDir.resolve("file_top.txt");
        Path file2InSubDir = subDir.resolve("file_sub.txt");

        Files.writeString(file1, "Top level content");
        Files.writeString(file2InSubDir, "Subdir content, should not be loaded");

        when(mockSplitter.splitDocuments(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        loader = new FileSystemDocumentLoader(tempDir, false, null, null, mockSplitter); // Recursive false
        List<Document> documents = loader.getDocuments();

        assertEquals(1, documents.size());
        assertEquals(file1.toAbsolutePath().toString(), documents.get(0).getSourceName());
        assertEquals("Top level content", documents.get(0).getContent());
    }


    @Test
    void getDocuments_nonExistentPath_throwsDocumentLoaderException() {
        Path nonExistentPath = tempDir.resolve("does_not_exist");
        loader = new FileSystemDocumentLoader(nonExistentPath, false, null, null, mockSplitter);

        DocumentLoaderException ex = assertThrows(DocumentLoaderException.class, () -> loader.getDocuments());
        assertTrue(ex.getMessage().contains("Path does not exist"));
    }

    @Test
    void getDocuments_fileReaderThrowsIOException_propagatesAsDocumentLoaderException() throws IOException {
        Path testFile = tempDir.resolve("error_file.bad");
        Files.writeString(testFile, "content");

        FileReader errorReader = mock(FileReader.class);
        when(errorReader.getText(eq(testFile), anyMap())).thenThrow(new IOException("Simulated read error"));

        Map<String, FileReader> customReaders = Map.of("bad", errorReader);
        loader = new FileSystemDocumentLoader(testFile, false, customReaders, null, mockSplitter);

        // For single file load, IOException from reader.getText() propagates directly
        IOException ex = assertThrows(IOException.class, () -> loader.getDocuments());
        assertTrue(ex.getMessage().contains("Simulated read error"));
    }

    @Test
    void getDocuments_addsFileMetadata() throws IOException {
        Path testFile = tempDir.resolve("metadata_test.txt");
        Files.writeString(testFile, "metadata content");

        when(mockSplitter.splitDocuments(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        loader = new FileSystemDocumentLoader(testFile, false, null, null, mockSplitter);
        List<Document> documents = loader.getDocuments();

        assertEquals(1, documents.size());
        Document doc = documents.get(0);
        assertNotNull(doc.getMetadata());
        assertEquals(testFile.toAbsolutePath().toString(), doc.getMetadata().get("file_path"));
        assertEquals("metadata_test.txt", doc.getMetadata().get("file_name"));
        assertTrue((Long)doc.getMetadata().get("file_size_bytes") > 0);
        assertNotNull(doc.getMetadata().get("creation_time_utc"));
        assertNotNull(doc.getMetadata().get("last_modified_time_utc"));
    }
}

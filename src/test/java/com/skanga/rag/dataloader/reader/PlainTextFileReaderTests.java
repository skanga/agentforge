package com.skanga.rag.dataloader.reader;

import com.skanga.rag.dataloader.DocumentLoaderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class PlainTextFileReaderTests {

    @TempDir
    Path tempDir;

    private final PlainTextFileReader reader = new PlainTextFileReader();

    @Test
    void getText_validFile_returnsContent() throws IOException, DocumentLoaderException {
        Path testFile = tempDir.resolve("test.txt");
        String expectedContent = "Hello, world!\nThis is a test file.";
        Files.writeString(testFile, expectedContent, StandardCharsets.UTF_8);

        String actualContent = reader.getText(testFile, Collections.emptyMap());
        assertEquals(expectedContent, actualContent);
    }

    @Test
    void getText_emptyFile_returnsEmptyString() throws IOException, DocumentLoaderException {
        Path testFile = tempDir.resolve("empty.txt");
        Files.createFile(testFile);

        String actualContent = reader.getText(testFile, Collections.emptyMap());
        assertEquals("", actualContent);
    }

    @Test
    void getText_fileNotFound_throwsDocumentLoaderException() {
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");
        DocumentLoaderException ex = assertThrows(DocumentLoaderException.class, () -> {
            reader.getText(nonExistentFile, Collections.emptyMap());
        });
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @Test
    void getText_fileNotReadable_throwsDocumentLoaderException(@TempDir Path tempDirNonReadable) throws IOException {
        Path testFile = tempDirNonReadable.resolve("unreadable.txt");
        Files.writeString(testFile, "content", StandardCharsets.UTF_8);

        // On POSIX systems, setting readable to false. Windows behavior might differ.
        if (testFile.toFile().setReadable(false)) {
            DocumentLoaderException ex = assertThrows(DocumentLoaderException.class, () -> {
                reader.getText(testFile, Collections.emptyMap());
            });
            assertTrue(ex.getMessage().contains("is not readable"));
            // Restore readability for cleanup if possible, though @TempDir handles it.
            testFile.toFile().setReadable(true);
        } else {
            System.err.println("Warning: Could not make file unreadable for PlainTextFileReaderTests. Test for unreadable file might not be effective.");
        }
    }

    // Test for different encodings would require a way to pass encoding via options
    // and the reader to respect it. Current PlainTextFileReader defaults to UTF-8.
    // @Test
    // void getText_withDifferentEncoding_readsCorrectly() throws IOException, DocumentLoaderException {
    //     Path testFile = tempDir.resolve("encoded_test.txt");
    //     String content = "你好世界"; // Example non-ASCII content
    //     Files.writeString(testFile, content, StandardCharsets.UTF_16);
    //
    //     // This would fail as reader uses UTF-8
    //     // String actualContent = reader.getText(testFile, Map.of("encoding", StandardCharsets.UTF_16BE));
    //     // assertEquals(content, actualContent);
    //     assertTrue(true, "Test for different encodings needs reader to support options.");
    // }
}

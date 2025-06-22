package com.skanga.rag.dataloader.reader;

import com.skanga.rag.dataloader.DocumentLoaderException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Interface for file readers.
 * Implementations of this interface are responsible for extracting textual content
 * from specific file types (e.g., plain text, PDF, DOCX).
 *
 * <p>This is typically used by {@link com.skanga.rag.dataloader.FileSystemDocumentLoader}
 * to handle different file formats found in a directory.</p>
 */
public interface FileReader {

    /**
     * Extracts text content from the file specified by the given path.
     *
     * @param filePath The {@link Path} to the file from which to extract text.
     *                 The file is expected to exist and be readable.
     * @param options  An optional map of options that might be relevant for specific
     *                 file readers (e.g., "encoding" for text files, "password" for
     *                 password-protected PDFs). Implementations should define what
     *                 options they support. Can be null if no options are needed.
     * @return The extracted text content as a String. Should not be null; can be an empty string
     *         if the file is empty or contains no extractable text.
     * @throws IOException if an I/O error occurs during file reading.
     * @throws DocumentLoaderException if an error specific to parsing the file content occurs
     *                                 (e.g., malformed PDF, unsupported format for the reader).
     */
    String getText(Path filePath, Map<String, Object> options) throws IOException, DocumentLoaderException;
}

package com.skanga.rag.dataloader.reader;

import com.skanga.rag.dataloader.DocumentLoaderException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset; // For future use with options
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * A {@link FileReader} implementation for reading plain text files.
 * It reads the entire content of a file into a single string.
 *
 * <p>Currently uses UTF-8 as the default character encoding. This could be made
 * configurable via the `options` map in the {@link #getText(Path, Map)} method
 * if different encodings need to be supported.</p>
 */
public class PlainTextFileReader implements FileReader {

    /**
     * {@inheritDoc}
     * <p>Reads all bytes from the specified file path and converts them to a string
     * using UTF-8 encoding.</p>
     *
     * @param filePath The path to the plain text file.
     * @param options  Optional map. Could be used in the future to specify encoding
     *                 (e.g., key "encoding", value instance of {@link Charset} or charset name string).
     *                 Currently, these options are ignored.
     * @throws DocumentLoaderException if the file does not exist or is not readable.
     * @throws IOException if an I/O error occurs reading the file.
     */
    @Override
    public String getText(Path filePath, Map<String, Object> options) throws IOException, DocumentLoaderException {
        if (!Files.exists(filePath)) {
            throw new DocumentLoaderException("Plain text file does not exist: " + filePath);
        }
        if (!Files.isReadable(filePath)) {
            throw new DocumentLoaderException("Plain text file is not readable: " + filePath);
        }
        try {
            // Consider using options to specify charset if provided, e.g.:
            // Charset charset = options != null && options.get("encoding") instanceof Charset ?
            //                   (Charset) options.get("encoding") : StandardCharsets.UTF_8;
            // return Files.readString(filePath, charset); // Java 11+
            // For large files, use streaming approach
            long fileSize = Files.size(filePath);
            if (fileSize > 10_000_000) { // 10MB threshold
                return Files.readString(filePath, StandardCharsets.UTF_8);
            } else {
                // Use buffered reading for better memory management
                StringBuilder content = new StringBuilder((int) fileSize);
                try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                    char[] buffer = new char[8192];
                    int charsRead;
                    while ((charsRead = reader.read(buffer)) != -1) {
                        content.append(buffer, 0, charsRead);
                    }
                }
                return content.toString();
            }
        } catch (IOException e) {
            throw new IOException("Error reading plain text file: " + filePath, e); // Chain original IOException
        } catch (Exception e) {
            // Catch any other unexpected errors during file reading
            throw new DocumentLoaderException("Failed to read plain text file: " + filePath, e);
        }
    }
}

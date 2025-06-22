package com.skanga.rag.dataloader;

import com.skanga.rag.Document;
import com.skanga.rag.dataloader.reader.FileReader;
import com.skanga.rag.dataloader.reader.PdfTextFileReader;
import com.skanga.rag.dataloader.reader.PlainTextFileReader;
import com.skanga.rag.splitter.TextSplitter; // For constructor type hint

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link DocumentLoader} that loads documents from the local file system.
 * It can load a single file or recursively scan a directory for files.
 *
 * <p><b>File Type Handling:</b>
 * It uses a map of {@link FileReader} instances to process different file extensions.
 * Default readers are provided for common types like ".txt" (using {@link PlainTextFileReader})
 * and ".pdf" (using {@link PdfTextFileReader}). Custom readers for other extensions can be supplied.
 * If no specific reader is found for an extension, it defaults to trying {@code PlainTextFileReader}.
 * </p>
 *
 * <p><b>Document Creation:</b>
 * For each loaded file, a {@link Document} object is created.
 * <ul>
 *   <li>{@code Document.content} is set to the text extracted by the appropriate {@code FileReader}.</li>
 *   <li>{@code Document.sourceType} is set to "file".</li>
 *   <li>{@code Document.sourceName} is set to the absolute path of the file.</li>
 *   <li>Basic file metadata (path, name, size, creation/modification/access times) is added
 *       to {@code Document.metadata}.</li>
 * </ul>
 * </p>
 *
 * <p><b>Text Splitting:</b>
 * After loading the raw content of all files, this loader applies the configured
 * {@link TextSplitter} (from {@link AbstractDocumentLoader}) to the list of created documents,
 * potentially breaking them into smaller chunks before returning.
 * </p>
 */
public class FileSystemDocumentLoader extends AbstractDocumentLoader {

    private final Path path; // Path to the file or directory to load from
    final Map<String, FileReader> readers; // Maps file extension (lowercase) to a FileReader
    private final boolean recursive; // Whether to scan directories recursively
    private final Map<String, Object> readerOptions; // Common options to pass to all FileReaders

    /**
     * Constructs a FileSystemDocumentLoader with detailed configuration.
     *
     * @param path           The {@link Path} to the file or directory to load. Must not be null.
     * @param recursive      If true, subdirectories will be scanned recursively. Ignored if `path` is a file.
     * @param customReaders  A map of custom {@link FileReader}s where keys are lowercase file extensions
     *                       (e.g., "docx") and values are {@code FileReader} instances. These override defaults. Can be null.
     * @param readerOptions  A map of options to pass to each {@code FileReader}'s {@code getText} method. Can be null.
     * @param textSplitter   The {@link TextSplitter} to use for splitting loaded documents. Must not be null.
     */
    public FileSystemDocumentLoader(Path path, boolean recursive, Map<String, FileReader> customReaders, Map<String, Object> readerOptions, TextSplitter textSplitter) {
        super(textSplitter); // Pass splitter to AbstractDocumentLoader
        Objects.requireNonNull(path, "Path cannot be null for FileSystemDocumentLoader.");
        this.path = path;
        this.recursive = recursive;

        this.readers = new HashMap<>();
        // Initialize with default readers
        this.readers.put("txt", new PlainTextFileReader());
        this.readers.put("md", new PlainTextFileReader());
        this.readers.put("json", new PlainTextFileReader());
        this.readers.put("log", new PlainTextFileReader());
        this.readers.put("csv", new PlainTextFileReader());
        this.readers.put("xml", new PlainTextFileReader());
        this.readers.put("html", new PlainTextFileReader()); // Basic text extraction from HTML
        this.readers.put("pdf", new PdfTextFileReader());   // Requires PDFBox dependency

        if (customReaders != null) {
            this.readers.putAll(customReaders); // Custom readers override defaults
        }
        this.readerOptions = readerOptions != null ? Collections.unmodifiableMap(new HashMap<>(readerOptions)) : Collections.emptyMap();
    }

    /**
     * Constructs a FileSystemDocumentLoader with specified path, recursion, and text splitter.
     * Uses default file readers and no specific reader options.
     * @param path Path to the file or directory.
     * @param recursive True to scan directories recursively.
     * @param textSplitter The text splitter to use.
     */
    public FileSystemDocumentLoader(Path path, boolean recursive, TextSplitter textSplitter) {
        this(path, recursive, null, null, textSplitter);
    }

    /**
     * Constructs a FileSystemDocumentLoader that is not recursive, using a specified text splitter.
     * @param path Path to the file or directory.
     * @param textSplitter The text splitter to use.
     */
    public FileSystemDocumentLoader(Path path, TextSplitter textSplitter) {
        this(path, false, null, null, textSplitter);
    }

    /**
     * Constructs a FileSystemDocumentLoader with specified path.
     * Uses default text splitter from {@link AbstractDocumentLoader}, is not recursive,
     * and uses default file readers.
     * @param path Path to the file or directory.
     */
    public FileSystemDocumentLoader(Path path) {
        super(); // Uses default TextSplitter
        Objects.requireNonNull(path, "Path cannot be null.");
        this.path = path;
        this.recursive = false;
        this.readers = new HashMap<>(); // Initialize default readers here as well
        this.readers.put("txt", new PlainTextFileReader());
        this.readers.put("pdf", new PdfTextFileReader());
        this.readerOptions = Collections.emptyMap();
    }


    /**
     * {@inheritDoc}
     * <p>Loads documents from the configured file or directory. If a directory, it's scanned
     * (recursively or not based on settings). Each found file is read using an appropriate
     * {@link FileReader} based on its extension. The resulting list of raw documents is then
     * processed by the configured {@link TextSplitter}.</p>
     *
     * @throws DocumentLoaderException if the path is invalid or no suitable reader is found for a file.
     * @throws IOException if an I/O error occurs during file system access or file reading.
     */
    @Override
    public List<Document> getDocuments() throws IOException, DocumentLoaderException {
        List<Document> rawDocuments = new ArrayList<>();
        if (Files.isDirectory(path)) {
            int maxDepth = recursive ? Integer.MAX_VALUE : 1;
            // Using try-with-resources for the stream to ensure it's closed.
            try (Stream<Path> fileStream = Files.walk(path, maxDepth)) {
                fileStream
                        .filter(Files::isRegularFile)
                        .filter(Files::isReadable)
                        .parallel() // Add parallel processing
                        .forEach(filePath -> {
                            try {
                                Document doc = loadDocumentFromFile(filePath);
                                synchronized (rawDocuments) { // Synchronize list access
                                    rawDocuments.add(doc);
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(new DocumentLoaderException(
                                        "Failed to load document from file: " + filePath + ". Error: " + e.getMessage(), e));
                            }
                        });
            } catch (IOException e) {
                 throw new DocumentLoaderException("Error walking directory: " + path, e);
            }



        } else if (Files.isRegularFile(path) && Files.isReadable(path)) {
            rawDocuments.add(loadDocumentFromFile(path));
        } else if (!Files.exists(path)){
            throw new DocumentLoaderException("Path does not exist: " + path);
        } else {
            throw new DocumentLoaderException("Path is not a readable file or directory: " + path);
        }

        // Apply text splitting to all loaded raw documents
        return this.textSplitter.splitDocuments(rawDocuments);
    }

    /**
     * Loads content from a single file and creates a {@link Document}.
     * @param filePath The path to the file.
     * @return The created Document.
     * @throws IOException if file reading fails.
     * @throws DocumentLoaderException if no reader is found or text extraction fails.
     */
    private Document loadDocumentFromFile(Path filePath) throws IOException, DocumentLoaderException {
        String extension = getFileExtension(filePath.getFileName().toString()).toLowerCase();
        // Default to PlainTextFileReader if specific reader not found, or if extension is empty.
        FileReader reader = readers.getOrDefault(extension, readers.get("txt"));

        if (reader == null) {
             // This should not be reached if "txt" reader is always in the map as a default.
             // If it were possible, it'd mean "txt" default was missing.
             throw new DocumentLoaderException("No suitable reader (not even default) found for file: " + filePath);
        }

        String content = reader.getText(filePath, this.readerOptions);
        Document doc = new Document(content);
        doc.setSourceType("file");
        doc.setSourceName(filePath.toAbsolutePath().toString()); // Use absolute path for sourceName

        // Add basic file metadata
        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            doc.addMetadata("file_path", filePath.toAbsolutePath().toString());
            doc.addMetadata("file_name", filePath.getFileName().toString());
            doc.addMetadata("file_size_bytes", attrs.size());
            doc.addMetadata("creation_time_utc", attrs.creationTime().toString());
            doc.addMetadata("last_modified_time_utc", attrs.lastModifiedTime().toString());
            doc.addMetadata("last_access_time_utc", attrs.lastAccessTime().toString());
        } catch (IOException e) {
            // Log as a warning, but don't fail the document loading itself if attributes can't be read.
            System.err.println("Warning: Could not read file attributes for " + filePath + ": " + e.getMessage());
        }
        return doc;
    }

    /**
     * Utility to get the lowercase file extension from a file name.
     * @param fileName The name of the file.
     * @return The extension (without the dot), or an empty string if no extension.
     */
    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int lastIndexOfDot = fileName.lastIndexOf(".");
        if (lastIndexOfDot == -1 || lastIndexOfDot == fileName.length() - 1) {
            return ""; // No extension or ends with a dot
        }
        return fileName.substring(lastIndexOfDot + 1).toLowerCase();
    }
}

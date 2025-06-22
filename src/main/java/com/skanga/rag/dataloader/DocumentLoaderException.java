package com.skanga.rag.dataloader;

/**
 * Custom runtime exception for errors that occur during the document loading process.
 * This can be thrown by {@link DocumentLoader} implementations or their helper classes
 * (like {@link com.skanga.rag.dataloader.reader.FileReader}) to indicate issues such as:
 * <ul>
 *   <li>File not found or not readable.</li>
 *   <li>Errors parsing file content (e.g., malformed PDF, unsupported encoding).</li>
 *   <li>Network errors if loading from a URL.</li>
 *   <li>Configuration errors specific to a loader.</li>
 * </ul>
 * It extends {@link RuntimeException}, making it an unchecked exception.
 */
public class DocumentLoaderException extends RuntimeException {

    /**
     * Constructs a new document loader exception with the specified detail message.
     * @param message the detail message.
     */
    public DocumentLoaderException(String message) {
        super(message);
    }

    /**
     * Constructs a new document loader exception with the specified detail message and cause.
     * @param message the detail message.
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
     *              A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.
     */
    public DocumentLoaderException(String message, Throwable cause) {
        super(message, cause);
    }
}

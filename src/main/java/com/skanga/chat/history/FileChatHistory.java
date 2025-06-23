package com.skanga.chat.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.skanga.chat.exceptions.ChatHistoryException; // Using this specific exception
import com.skanga.chat.messages.Message;

import java.io.BufferedReader; // For init
import java.io.BufferedWriter; // For updateFile
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A file-based implementation of {@link ChatHistory}.
 * Messages are persisted as JSON objects (one per line) in a specified file.
 * This provides simple persistence across application restarts.
 *
 * Relies on {@link AbstractChatHistory} for in-memory message management and
 * serialization/deserialization logic ({@code toJsonSerializable()} and {@code deserializeMessages()}).
 *
 * Note on Thread Safety:
 * File operations are synchronized on `this` instance to prevent concurrent modification
 * issues if multiple threads were to interact with the same `FileChatHistory` instance.
 * However, if multiple `FileChatHistory` instances point to the *same file path*
 * from different processes or classloaders, external file locking mechanisms would be needed,
 * which are not implemented here. This implementation is process-safe for a single instance.
 */
public class FileChatHistory extends AbstractChatHistory {

    private final Path filePath;
    private final ObjectMapper objectMapper; // For JSON processing

    /** Default context window size for file history if not specified. */
    private static final int DEFAULT_FILE_CONTEXT_WINDOW = 100;
    /** Default file name if only directory is provided. */
    private static final String DEFAULT_HISTORY_FILE_NAME = "chat_history.jsonl";


    /**
     * Constructs a FileChatHistory with specified context window, directory, and file details.
     *
     * @param contextWindow  The maximum number of messages to retain in history.
     * @param directoryPath  The directory where the history file will be stored.
     * @param fileName       The name of the history file (e.g., "user123_history.jsonl").
     * @param filePrefix     A prefix for the file name (can be empty, e.g., for user-specific files). Deprecated: use full fileName.
     * @param fileExtension  The extension for the file (e.g., "jsonl"). Deprecated: use full fileName.
     * @throws ChatHistoryException if the directory cannot be created or file cannot be initialized.
     * @deprecated Use constructor without prefix and extension; include them in `fileName`.
     */
    @Deprecated
    public FileChatHistory(int contextWindow, String directoryPath, String fileName, String filePrefix, String fileExtension) throws ChatHistoryException {
        super(contextWindow);
        Objects.requireNonNull(directoryPath, "Directory path cannot be null.");
        String effectiveFileName = (filePrefix != null ? filePrefix : "") +
                                   (fileName != null ? fileName : DEFAULT_HISTORY_FILE_NAME) +
                                   (fileExtension != null && !fileExtension.startsWith(".") ? "." + fileExtension : (fileExtension != null ? fileExtension : ""));
        this.filePath = Paths.get(directoryPath, effectiveFileName);

        this.objectMapper = new ObjectMapper();
        // DO NOT use INDENT_OUTPUT for JSONL (one JSON object per line)
        // this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        init();
    }

    /**
     * Constructs a FileChatHistory with specified context window, directory, and full file name.
     *
     * @param contextWindow  The maximum number of messages to retain in history.
     * @param directoryPath  The directory where the history file will be stored.
     * @param fullFileName   The full name of the history file (e.g., "chat_session_xyz.jsonl").
     * @throws ChatHistoryException if the directory cannot be created or file cannot be initialized.
     */
    public FileChatHistory(int contextWindow, String directoryPath, String fullFileName) throws ChatHistoryException {
        super(contextWindow);
        Objects.requireNonNull(directoryPath, "Directory path cannot be null.");
        Objects.requireNonNull(fullFileName, "Full file name cannot be null.");
        this.filePath = Paths.get(directoryPath, fullFileName);

        this.objectMapper = new ObjectMapper();
        // DO NOT use INDENT_OUTPUT for JSONL (one JSON object per line)
        // this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        init();
    }


    /**
     * Constructs a FileChatHistory with default context window size.
     * @param directoryPath  The directory for the history file.
     * @param fullFileName   The full name of the history file.
     * @throws ChatHistoryException if initialization fails.
     */
    public FileChatHistory(String directoryPath, String fullFileName) throws ChatHistoryException {
        this(DEFAULT_FILE_CONTEXT_WINDOW, directoryPath, fullFileName);
    }


    /**
     * Initializes the chat history. If a history file exists at the specified path,
     * it loads messages from it. Otherwise, creates necessary directories and an empty file.
     * This method is synchronized to prevent race conditions on file initialization.
     * @throws ChatHistoryException if directory creation or file reading fails.
     */
    private synchronized void init() throws ChatHistoryException {
        try {
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            if (Files.exists(filePath)) {
                if (Files.size(filePath) > 0) { // Check if file is not empty before trying to read
                    List<Map<String, Object>> messagesData = new ArrayList<>();
                    // Read line by line, as each line is a JSON object for a message
                    try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                        String line;
                        while((line = reader.readLine()) != null) {
                            if (!line.trim().isEmpty()) {
                                messagesData.add(objectMapper.readValue(line, new TypeReference<Map<String, Object>>() {}));
                            }
                        }
                    }
                    // this.history is inherited from AbstractChatHistory
                    super.deserializeMessages(messagesData); // Use superclass method to populate history
                } else {
                    this.history = new ArrayList<>(); // Initialize empty history if file is empty
                }
            } else {
                Files.createFile(filePath); // Create empty file
                this.history = new ArrayList<>();
            }
        } catch (IOException e) {
            throw new ChatHistoryException("Failed to initialize chat history from file: " + filePath, e);
        }
    }

    /**
     * Gets the file path where this chat history is stored.
     * @return The {@link Path} to the history file.
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * {@inheritDoc}
     * For FileChatHistory, this persists the current state of the entire history
     * (including the newly added message) to the file by rewriting it.
     * This method is called by {@link AbstractChatHistory#addMessage(Message)}.
     */
    @Override
    protected synchronized void storeMessage(Message message) throws ChatHistoryException {
        // AbstractChatHistory.addMessage already adds it to the in-memory 'this.history' list.
        // This method's role is to persist the *entire current state* of 'this.history'.
        updateFile();
    }

    /**
     * {@inheritDoc}
     * For FileChatHistory, this clears the content of the history file.
     * This method is called by {@link AbstractChatHistory#flushAll()}.
     */
    @Override
    protected synchronized void clear() throws ChatHistoryException {
        // AbstractChatHistory.flushAll already clears the in-memory 'this.history' list.
        // This method's role is to persist this cleared state (empty file).
        updateFile();
    }

    /**
     * {@inheritDoc}
     * Overrides to ensure that after removing the oldest message from the in-memory list
     * (done by super.removeOldestMessage() or by cutHistoryToContextWindow calling it),
     * the file is updated to reflect this change.
     */
    @Override
    public synchronized void removeOldestMessage() throws ChatHistoryException {
        if (!this.history.isEmpty()) { // Check if there's anything to remove
            super.removeOldestMessage(); // Removes from in-memory list
            updateFile(); // Persist the change
        }
    }


    /**
     * Writes the current in-memory chat history to the file, overwriting existing content.
     * Each message is serialized to a JSON string and written on a new line.
     * This method is synchronized to prevent concurrent writes.
     * @throws ChatHistoryException if an error occurs during file writing or JSON serialization.
     */
    private synchronized void updateFile() throws ChatHistoryException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            List<Map<String, Object>> serializableHistory = toJsonSerializable(); // Method from AbstractChatHistory
            for (Map<String, Object> messageMap : serializableHistory) {
                String jsonMessage = objectMapper.writeValueAsString(messageMap);
                writer.write(jsonMessage);
                writer.newLine();
            }
        } catch (JsonProcessingException e) {
            throw new ChatHistoryException("Failed to serialize chat history to JSON for file: " + filePath, e);
        } catch (IOException e) {
            throw new ChatHistoryException("Failed to update chat history file: " + filePath, e);
        }
    }
}

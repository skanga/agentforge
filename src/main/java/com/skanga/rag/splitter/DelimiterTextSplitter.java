package com.skanga.rag.splitter;

import com.skanga.rag.Document;
import com.skanga.rag.dataloader.DocumentLoaderException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections; // For Collections.emptyList()
import java.util.HashMap; // For copying metadata
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link TextSplitter} that splits text based on a specified delimiter string
 * and aims to create chunks that do not exceed a maximum length.
 * It also supports configurable word-based overlap between chunks.
 *
 * <p><b>Splitting Logic:</b>
 * <ol>
 *   <li>The input text is first split into parts using the specified {@code separator}.</li>
 *   <li>These parts are then iteratively combined to form chunks.</li>
 *   <li>If adding the next part (plus a separator) to the current chunk would exceed
 *       {@code maxLength}, the current chunk is finalized and added to the results.</li>
 *   <li><b>Overlap:</b> When a chunk is finalized, a specified number of words ({@code wordOverlap})
 *       from the end of that chunk are taken and prepended to the beginning of the next new chunk.
 *       This helps maintain context continuity across chunks.</li>
 * </ol>
 * </p>
 *
 * <p><b>Considerations:</b>
 * <ul>
 *   <li>The {@code maxLength} is a target, but chunks might sometimes slightly exceed it if a single
 *       part (an element resulting from the initial split by {@code separator}) is itself longer
 *       than {@code maxLength}. The current logic doesn't further split these oversized parts.</li>
 *   <li>Word overlap is based on splitting text by whitespace. This might behave differently
 *       for languages that don't use whitespace word separation.</li>
 * </ul>
 * </p>
 */
public class DelimiterTextSplitter implements TextSplitter {

    private final int maxLength;
    private final String separator;
    private final int wordOverlap; // Number of words for overlap

    /** Default maximum character length for a chunk. */
    public static final int DEFAULT_MAX_LENGTH = 1000;
    /** Default separator string (e.g., double newline for paragraphs). */
    public static final String DEFAULT_SEPARATOR = "\n\n";
    /** Default number of words for overlap. */
    public static final int DEFAULT_WORD_OVERLAP = 50;

    /**
     * Constructs a DelimiterTextSplitter with default settings:
     * maxLength=1000, separator="\n\n", wordOverlap=50.
     */
    public DelimiterTextSplitter() {
        this(DEFAULT_MAX_LENGTH, DEFAULT_SEPARATOR, DEFAULT_WORD_OVERLAP);
    }

    /**
     * Constructs a DelimiterTextSplitter with specified parameters.
     *
     * @param maxLength   The target maximum character length for each chunk.
     * @param separator   The string delimiter used to initially split the text.
     * @param wordOverlap The number of words from the end of a chunk to prepend to the next chunk.
     * @throws IllegalArgumentException if maxLength is not positive or wordOverlap is negative.
     */
    public DelimiterTextSplitter(int maxLength, String separator, int wordOverlap) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("Max length must be positive.");
        }
        if (wordOverlap < 0) {
            throw new IllegalArgumentException("Word overlap cannot be negative.");
        }
        Objects.requireNonNull(separator, "Separator cannot be null.");
        this.maxLength = maxLength;
        this.separator = separator;
        this.wordOverlap = wordOverlap;
    }

    @Override
    public List<Document> splitDocument(Document document) {
        Objects.requireNonNull(document, "Document to split cannot be null.");
        String text = document.getContent();

        if (text == null || text.isEmpty()) {
            // Create a new document with empty content but other fields copied
            Document emptyContentDoc = new Document("");
            emptyContentDoc.setId(document.getId()); // Keep original ID if desired, or let new one generate
            emptyContentDoc.setSourceName(document.getSourceName());
            emptyContentDoc.setSourceType(document.getSourceType());
            if (document.getMetadata() != null) {
                 emptyContentDoc.setMetadata(new HashMap<>(document.getMetadata()));
            }
            return List.of(emptyContentDoc);
        }

        // Initial split by the main separator
        String[] parts = text.split(Pattern.quote(this.separator), -1); // -1 to keep trailing empty strings if separator is at end
                                                                    // Pattern.quote to treat separator literally

        List<String> chunksContents = createChunksFromParts(Arrays.asList(parts));

        // If splitting resulted in a single chunk identical to the original text,
        // return the original document to preserve its ID and other attributes directly.
        if (chunksContents.size() == 1 && chunksContents.get(0).equals(text)) {
            return List.of(document);
        }

        List<Document> resultDocuments = new ArrayList<>();
        for (String chunkContent : chunksContents) {
            Document chunkDoc = new Document(chunkContent); // New ID will be generated
            // Propagate source and metadata from the original document
            chunkDoc.setSourceName(document.getSourceName());
            chunkDoc.setSourceType(document.getSourceType());
            if (document.getMetadata() != null) {
                chunkDoc.setMetadata(new HashMap<>(document.getMetadata())); // Create a copy of metadata
            }
            // Note: Embeddings are not propagated; each new chunk document needs its own embedding.
            resultDocuments.add(chunkDoc);
        }
        return resultDocuments;
    }

    /**
     * Creates chunks from pre-split parts of text.
     * @param parts List of text segments (already split by the main separator).
     * @return List of chunked strings.
     */
    private List<String> createChunksFromParts(List<String> parts) {
        // System.out.println("DelimiterTextSplitter.createChunksFromParts DEBUG: Separator='" + this.separator.replace("\n", "\\n") + "', InputParts: " + parts); // DEBUG REMOVED
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder(maxLength);
        List<String> wordsForOverlap = new ArrayList<>();

        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);

            // If currentChunk is empty and this part is also empty (e.g. multiple separators together),
            // and it's not the very start (where we might want an initial empty chunk if text starts with separator)
            // then we can skip creating an entirely empty chunk from just separators.
            if (currentChunk.length() == 0 && part.isEmpty() && !wordsForOverlap.isEmpty()) {
                 // If we have overlap words, means previous chunk was just added.
                 // An empty part now means consecutive separators. Add the separator to the overlap.
                 if (!wordsForOverlap.isEmpty() && !this.separator.isEmpty()){ // Add separator if not empty
                    String lastWord = wordsForOverlap.get(wordsForOverlap.size()-1);
                    // Avoid adding separator if overlap already ends with it or part of it
                    if(!lastWord.endsWith(this.separator)){
                        // This logic for adding separator to overlap is complex.
                        // Simpler: overlap is just text. Separator re-added when joining.
                    }
                 }
                continue;
            }


            // Calculate length if this part is added (including separator if chunk isn't empty)
            int lengthWithPart = currentChunk.length() +
                                 (currentChunk.length() > 0 && !part.isEmpty() ? separator.length() : 0) +
                                 part.length();

            if (lengthWithPart > maxLength && currentChunk.length() > 0) {
                // Current chunk is full, finalize it
                chunks.add(currentChunk.toString());

                // Prepare for next chunk with overlap
                currentChunk = new StringBuilder();
                if (wordOverlap > 0 && !wordsForOverlap.isEmpty()) {
                    int startIndex = Math.max(0, wordsForOverlap.size() - wordOverlap);
                    List<String> overlapSlice = wordsForOverlap.subList(startIndex, wordsForOverlap.size());
                    currentChunk.append(String.join(" ", overlapSlice));
                    if (!overlapSlice.isEmpty()) currentChunk.append(" "); // Add space after overlap
                }
                wordsForOverlap.clear(); // Clear for the new chunk's words
            }

            // Add separator if current chunk is not empty and current part is not empty
            // (or if we want to preserve separators that create empty parts)
            if (currentChunk.length() > 0 && (i > 0 || !part.isEmpty()) ) { // i > 0 ensures separator not at start unless part of overlap
                 // Only add separator if currentChunk has content AND (it's not the first part OR the part isn't empty)
                 // This avoids leading separator unless part of overlap, and avoids separator if part is empty.
                 // A simpler rule: if currentChunk is not empty, and we are about to append a non-empty part, add separator.
                 if (!part.isEmpty() || (currentChunk.length() > 0 && i < parts.size() -1 ) ) { // Add separator if content will follow or if it's an internal separator
                    // The PHP logic was: if ($currentChunk !== '') $currentChunk .= $this->separator;
                    // This means separator is added *before* the part if chunk is not empty.
                    currentChunk.append(separator);
                 }
            }
            currentChunk.append(part);

            // Update wordsForOverlap based on the content *actually added* to currentChunk
            // This is tricky; for simplicity, let's base overlap on words from the *finalized* chunk.
            // The current simplified approach uses wordsForOverlap from the previous chunk.
            // For more accurate overlap from current part:
            if (!part.trim().isEmpty()) { // Only consider non-empty parts for word accumulation for next overlap
                 wordsForOverlap.addAll(Arrays.asList(part.trim().split("\\s+")));
                 // Trim wordsForOverlap if it gets too large to save memory, though it's for one chunk build-up
                 if (wordsForOverlap.size() > maxLength / 3) { // Heuristic: avg word length 3 + spaces
                    wordsForOverlap = wordsForOverlap.subList(wordsForOverlap.size() - (maxLength/3), wordsForOverlap.size());
                 }
            }
        }

        // Add the last remaining chunk
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        } else if (chunks.isEmpty() && parts.size() == 1 && parts.get(0).isEmpty()) {
            // Handle case where input was a single empty string part (original text was empty)
            chunks.add("");
        }

        // Post-filter to remove any completely empty chunks that might have resulted from
        // separator-only inputs or complex overlap scenarios, unless a single empty chunk is the intent.
        if (chunks.size() > 1) {
            return chunks.stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());
        } else if (chunks.isEmpty() && parts.stream().anyMatch(s->!s.isEmpty())) {
            // If input was non-empty but chunks became empty (e.g. text was only separators)
            // return a list with one empty string to signify "processed to empty" vs "no input".
            return Collections.singletonList("");
        }


        return chunks;
    }


    @Override
    public List<Document> splitDocuments(List<Document> documents) {
        Objects.requireNonNull(documents, "Documents list to split cannot be null.");
        return documents.stream()
                .flatMap(doc -> {
                    try {
                        return splitDocument(doc).stream();
                    } catch (Exception e) {
                        // Log error for this specific document and skip it, or rethrow
                        System.err.println("Error splitting document ID " + doc.getId() + ": " + e.getMessage());
                        throw new DocumentLoaderException("Document splitting failed for ID: " + doc.getId(), e);                        // Optionally, return a stream of the original document if splitting fails critically for it
                        // TODO: Do we halt doc processing or not?
                        // return Stream.empty(); // Skip problematic document
                    }
                })
                .collect(Collectors.toList());
    }
}

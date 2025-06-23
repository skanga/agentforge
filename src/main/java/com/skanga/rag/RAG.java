package com.skanga.rag;

import com.skanga.core.BaseAgent;
import com.skanga.chat.messages.Message;
import com.skanga.core.messages.MessageRequest;
import com.skanga.rag.embeddings.EmbeddingProvider;
import com.skanga.rag.vectorstore.VectorStore;
import com.skanga.rag.postprocessing.PostProcessor;
import com.skanga.core.exceptions.AgentException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Stream;
import java.math.BigInteger;

/**
 * Implements a Retrieval Augmented Generation (RAG) agent.
 * This agent extends {@link BaseAgent} and enhances its functionality by:
 * <ol>
 *   <li>Retrieving relevant documents from a {@link VectorStore} based on the user's query.</li>
 *   <li>Applying optional {@link PostProcessor}s to refine the retrieved documents.</li>
 *   <li>Augmenting the agent's context (system instructions) with the content of these documents.</li>
 *   <li>Delegating to the superclass's chat/stream methods to generate a response using the augmented context.</li>
 * </ol>
 *
 * It requires an {@link EmbeddingProvider} to generate embeddings for queries and documents,
 * and a {@link VectorStore} to store and search document embeddings.
 *
 * <p><b>Key RAG Flow:</b>
 * <ol>
 *   <li>User asks a question ({@code Message}).</li>
 *   <li>{@code answer()} or {@code streamAnswer()} is called.</li>
 *   <li>{@code retrieval(question)}:
 *     <ol>
 *       <li>{@code retrieveDocuments(question)}:
 *         <ol>
 *           <li>Query text is embedded using {@code embeddingProvider.embedText()}.</li>
 *           <li>{@code vectorStore.similaritySearch()} is called with the query embedding.</li>
 *           <li>Retrieved documents are deduplicated (based on content MD5).</li>
 *           <li>{@code applyPostProcessors()} refines the document list.</li>
 *         </ol>
 *       </li>
 *       <li>{@code withDocumentsContext(retrievedDocs)}: The content of processed documents is formatted
 *           and injected into the agent's system instructions within {@code <EXTRA-CONTEXT>...</EXTRA-CONTEXT>} tags.
 *           Any previous extra context is removed.</li>
 *     </ol>
 *   </li>
 *   <li>The augmented question (with new instructions) is passed to {@code super.chat()} or {@code super.stream()}.</li>
 *   <li>The LLM generates a response using the provided documents as context.</li>
 * </ol>
 * Observer notifications are emitted at various stages of the RAG process.
 * </p>
 */
public class RAG extends BaseAgent {
    protected VectorStore vectorStore;
    protected EmbeddingProvider embeddingProvider;
    protected List<PostProcessor> postProcessors = new ArrayList<>();
    /** Default number of documents to retrieve from the vector store. */
    protected int topK = 5;

    /**
     * Default constructor. Initializes a RAG agent without specific providers.
     * Providers must be set using setters before use.
     */
    public RAG() {
        super();
    }

    /**
     * Constructs a RAG agent with the specified embedding provider and vector store.
     * @param embeddingProvider The provider for generating text/document embeddings. Must not be null.
     * @param vectorStore The vector store for storing and searching documents. Must not be null.
     */
    public RAG(EmbeddingProvider embeddingProvider, VectorStore vectorStore) {
        this();
        this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "EmbeddingProvider cannot be null.");
        this.vectorStore = Objects.requireNonNull(vectorStore, "VectorStore cannot be null.");
    }

    /**
     * Sets the {@link VectorStore} to be used by this RAG agent.
     * @param vectorStore The vector store instance.
     * @return This RAG agent instance for fluent chaining.
     */
    public RAG setVectorStore(VectorStore vectorStore) {
        this.vectorStore = Objects.requireNonNull(vectorStore, "VectorStore cannot be null.");
        return this;
    }

    /**
     * Sets the {@link EmbeddingProvider} to be used by this RAG agent.
     * @param embeddingProvider The embedding provider instance.
     * @return This RAG agent instance for fluent chaining.
     */
    public RAG setEmbeddingProvider(EmbeddingProvider embeddingProvider) {
        this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "EmbeddingProvider cannot be null.");
        return this;
    }

    /**
     * Sets the list of {@link PostProcessor}s to be applied to retrieved documents.
     * Replaces any existing post-processors.
     * @param postProcessors A list of post-processors. If null, an empty list is used.
     * @return This RAG agent instance for fluent chaining.
     */
    public RAG setPostProcessors(List<PostProcessor> postProcessors) {
        this.postProcessors = (postProcessors == null) ? new ArrayList<>() : new ArrayList<>(postProcessors);
        return this;
    }

    /**
     * Adds a single {@link PostProcessor} to the list of post-processors.
     * @param postProcessor The post-processor to add. If null, no action is taken.
     * @return This RAG agent instance for fluent chaining.
     */
    public RAG addPostProcessor(PostProcessor postProcessor) {
        if (postProcessor != null) {
            if (this.postProcessors == null) this.postProcessors = new ArrayList<>();
            this.postProcessors.add(postProcessor);
        }
        return this;
    }

    /**
     * Sets the number of top documents to retrieve from the vector store (topK).
     * @param topK The number of documents. Must be positive.
     * @return This RAG agent instance for fluent chaining.
     * @throws IllegalArgumentException if topK is not positive.
     */
    public RAG setTopK(int topK) {
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive.");
        }
        this.topK = topK;
        return this;
    }

    /**
     * Gets the currently configured {@link VectorStore}.
     * @return The vector store.
     * @throws AgentException if the vector store has not been set.
     */
    public VectorStore getVectorStore() {
        if (this.vectorStore == null) {
            throw new AgentException("VectorStore has not been set for RAG agent.");
        }
        return vectorStore;
    }

    /**
     * Gets the currently configured {@link EmbeddingProvider}.
     * @return The embedding provider.
     * @throws AgentException if the embedding provider has not been set.
     */
    public EmbeddingProvider getEmbeddingProvider() {
        if (this.embeddingProvider == null) {
            throw new AgentException("EmbeddingProvider has not been set for RAG agent.");
        }
        return embeddingProvider;
    }

    /**
     * Gets the list of currently configured post-processors.
     * @return An unmodifiable list of post-processors.
     */
    protected List<PostProcessor> getPostProcessors() {
        return Collections.unmodifiableList(postProcessors);
    }


    /**
     * Answers a question by first performing document retrieval, augmenting context,
     * and then calling the underlying agent's chat capability.
     *
     * @param question The user's question as a {@link Message}. The content of this message
     *                 is used for retrieval.
     * @return The AI's response as a {@link Message}.
     * @throws AgentException if retrieval or chat generation fails.
     */
    public Message answer(Message question) {
        Objects.requireNonNull(question, "Question message cannot be null.");
        notifyObservers("rag-answer-start", Map.of("question", question.getContent() != null ? question.getContent().toString() : ""));

        retrieval(question); // Augments agent's instructions with context

        // super.chat() expects a MessageRequest.
        Message response = super.chat(new MessageRequest(question));

        notifyObservers("rag-answer-stop", Map.of("response_content", response.getContent() != null ? response.getContent().toString() : ""));
        return response;
    }

    /**
     * Provides a streaming answer to a question by first performing document retrieval,
     * augmenting context, and then calling the underlying agent's streaming capability.
     *
     * @param question The user's question as a {@link Message}.
     * @return A {@link Stream} of string chunks representing the AI's response.
     * @throws AgentException if retrieval or stream initiation fails.
     */
    public Stream<String> streamAnswer(Message question) {
        Objects.requireNonNull(question, "Question message cannot be null.");
        notifyObservers("rag-streamanswer-start", Map.of("question", question.getContent() != null ? question.getContent().toString() : ""));

        retrieval(question); // Augments agent's instructions with context

        // super.stream() expects a MessageRequest.
        Stream<String> responseStream = super.stream(new MessageRequest(question));

        // Notify that streaming has started. Actual content will flow through the stream.
        // A more complex setup might wrap the stream to notify on its completion/error.
        notifyObservers("rag-streamanswer-streaming", Map.of("status", "initiated"));

        return responseStream.onClose(() -> {
            notifyObservers("rag-streamanswer-stop", Map.of("status", "closed"));
        });
    }

    /**
     * Performs the document retrieval and context augmentation steps.
     * @param question The user's question message.
     */
    protected void retrieval(Message question) {
        notifyObservers("rag-retrieval-start", question);
        List<Document> retrievedDocs = retrieveDocuments(question);
        withDocumentsContext(retrievedDocs);
        notifyObservers("rag-retrieval-stop", Map.of("retrieved_docs_count", retrievedDocs.size()));
    }

    /**
     * Augments the agent's system instructions with the content of the provided documents.
     * It wraps the document content within {@code <EXTRA-CONTEXT>...</EXTRA-CONTEXT>} tags.
     * Any existing context block with these tags is removed before adding the new one.
     *
     * @param documents The list of {@link Document} objects to use for context.
     * @return This RAG agent instance for fluent chaining.
     */
    public RAG withDocumentsContext(List<Document> documents) {
        String originalInstructions = super.getInstructions();
        if (originalInstructions == null) originalInstructions = "";

        // Remove any previously added <EXTRA-CONTEXT> blocks
        String contextFreeInstructions = BaseAgent.removeDelimitedContent(originalInstructions, "<EXTRA-CONTEXT>", "</EXTRA-CONTEXT>").trim();

        StringBuilder newContextContent = new StringBuilder(4096);
        if (documents != null && !documents.isEmpty()) {
            newContextContent.append("\n\n<EXTRA-CONTEXT>\n"); // Add newlines for better separation
            newContextContent.append("--- Relevant Information Start ---\n");
            for (Document doc : documents) {
                newContextContent.append("Source (").append(doc.getSourceName() != null ? doc.getSourceName() : "N/A").append("):\n");
                newContextContent.append("Content: ").append(doc.getContent()).append("\n"); // Added "Content: " prefix
                newContextContent.append("---\n");
            }
            newContextContent.append("--- Relevant Information End ---\n");
            newContextContent.append("</EXTRA-CONTEXT>\n");
        }

        String newInstructions = contextFreeInstructions + newContextContent.toString();
        // The super.withInstructions call will notify if there's an actual change to the base agent's instructions.
        // The reason for the change (RAG context update) can be inferred by the calling method or a more specific event.
        // For now, rely on BaseAgent's notification.
        super.withInstructions(newInstructions.trim());
        return this;
    }

    /**
     * Generates an MD5 hash for a given string. Used for deduplication.
     * @param input The string to hash.
     * @return The MD5 hash string.
     * @throws RuntimeException if MD5 algorithm is not found (should not happen in standard Java env).
     */
    private String generateMD5(String input) {
        if (input == null) return "null_input_md5_" + System.nanoTime(); // Handle null input to avoid NPE
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            BigInteger no = new BigInteger(1, messageDigest);
            String hashtext = no.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
            return hashtext;
        } catch (NoSuchAlgorithmException e) {
            // This should ideally not happen in a standard Java environment
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    /**
     * Retrieves relevant documents for a given question.
     * This involves embedding the question, performing a similarity search in the vector store,
     * deduplicating results, and applying post-processors.
     *
     * @param question The user's question as a {@link Message}.
     * @return A list of processed and relevant {@link Document} objects.
     * @throws AgentException if the question content is invalid or if embedding/search fails.
     */
    public List<Document> retrieveDocuments(Message question) {
        if (question.getContent() == null || !(question.getContent() instanceof String) || ((String)question.getContent()).trim().isEmpty()) {
            throw new AgentException("Question content must be a non-empty string for RAG retrieval.");
        }
        String queryText = ((String) question.getContent()).trim();

        notifyObservers("rag-vectorstore-embedding-query", Map.of("query", queryText));
        List<Double> queryEmbedding = getEmbeddingProvider().embedText(queryText); // Throws if provider not set

        Map<String, Object> searchEventData = new HashMap<>();
        searchEventData.put("query_text", queryText);
        searchEventData.put("query_embedding_size", queryEmbedding != null ? queryEmbedding.size() : 0);
        searchEventData.put("top_k", this.topK);
        notifyObservers("rag-vectorstore-searching", searchEventData);
        long searchStartTime = System.currentTimeMillis();
        List<Document> retrievedDocs;
        long searchDurationMs;

        try {
            retrievedDocs = getVectorStore().similaritySearch(queryEmbedding, this.topK); // Throws if provider not set
            searchDurationMs = System.currentTimeMillis() - searchStartTime;
        } catch (RuntimeException e) {
            searchDurationMs = System.currentTimeMillis() - searchStartTime;
            notifyObservers("rag-vectorstore-result", new com.skanga.observability.events.VectorStoreResult(
                getVectorStore().getClass().getSimpleName(), question, Collections.emptyList(), searchDurationMs
            ));
            throw new AgentException("Failed to retrieve documents from vector store: " + e.getMessage(), e);
        }

        // Deduplication: Using content hash to remove exact duplicates.
        // LinkedHashSet preserves insertion order of unique elements.
        Set<String> contentHashes = new LinkedHashSet<>();
        List<Document> uniqueDocs = new ArrayList<>();
        if (retrievedDocs != null) {
            for (Document doc : retrievedDocs) {
                if (doc.getContent() != null) {
                    String hash = generateMD5(doc.getContent());
                    if (contentHashes.add(hash)) { // add() returns true if element was new
                        uniqueDocs.add(doc);
                    }
                } else {
                    // Decide how to handle documents with null content during deduplication.
                    // For now, they are not added to uniqueDocs if content is null.
                    // If they should be preserved, add them directly: uniqueDocs.add(doc);
                }
            }
        }

        notifyObservers("rag-vectorstore-result", new com.skanga.observability.events.VectorStoreResult(
            getVectorStore().getClass().getSimpleName(), question, new ArrayList<>(uniqueDocs), searchDurationMs
        ));
        return applyPostProcessors(question, uniqueDocs);
    }

    /**
     * Applies registered post-processors to the list of documents.
     * @param question The original question message.
     * @param documents The list of documents to process.
     * @return The processed list of documents.
     */
    protected List<Document> applyPostProcessors(Message question, List<Document> documents) {
        List<Document> currentDocuments = new ArrayList<>(documents); // Work on a mutable copy
        if (this.postProcessors != null && !this.postProcessors.isEmpty()) {
            notifyObservers("rag-postprocessing-start", Map.of("processor_count", this.postProcessors.size(), "documents_in_count", currentDocuments.size()));
            for (PostProcessor processor : this.postProcessors) {
                Map<String, Object> processorStartData = Map.of(
                    "processor_name", processor.getClass().getSimpleName(),
                    "documents_in_count", currentDocuments.size()
                );
                notifyObservers("rag-postprocessor-applying", processorStartData);
                long ppStartTime = System.currentTimeMillis();

                currentDocuments = processor.process(question, currentDocuments);

                long ppDurationMs = System.currentTimeMillis() - ppStartTime;
                Map<String, Object> processorEndData = Map.of(
                    "processor_name", processor.getClass().getSimpleName(),
                    "documents_out_count", currentDocuments.size(),
                    "duration_ms", ppDurationMs
                );
                notifyObservers("rag-postprocessor-applied", processorEndData);
            }
            notifyObservers("rag-postprocessing-end", Map.of("final_document_count", currentDocuments.size()));
        }
        return currentDocuments;
    }

    /**
     * Adds documents to the vector store, including embedding them first.
     * @param documents The list of {@link Document} objects to add.
     *                  Their `embedding` field will be populated by this method.
     * @throws AgentException if embedding or adding to the vector store fails.
     */
    public void addDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        notifyObservers("rag-adddocuments-start", Map.of("document_count", documents.size()));

        // 1. Embed documents
        // The EmbeddingProvider interface was updated to return List<Document> with embeddings.
        // So, no need to manually assign embeddings back if using that contract.
        // However, the RAG class's addDocuments was initially written assuming embedDocuments returns List<List<Float>>.
        // Let's stick to the RAG class's current logic of assigning embeddings from List<List<Float>>.
        // This means EmbeddingProvider.embedDocuments should return List<List<Float>> as per its initial design in this context.
        // *Self-correction*: The EmbeddingProvider was indeed updated to return List<Document>.
        // So, the RAG class should use that.

        List<Document> embeddedDocuments;
        try {
            notifyObservers("rag-embedding-documents-start", Map.of("count", documents.size()));
            // Assuming embeddingProvider.embedDocuments updates and returns the documents with embeddings.
            embeddedDocuments = getEmbeddingProvider().embedDocuments(new ArrayList<>(documents)); // Pass a copy
            notifyObservers("rag-embedding-documents-end", Map.of("count", embeddedDocuments.size()));
        } catch (Exception e) { // Catch EmbeddingException or others
            throw new AgentException("Failed to embed documents for RAG: " + e.getMessage(), e);
        }

        // 2. Add to vector store
        notifyObservers("rag-vectorstore-adding-documents", Map.of("count", embeddedDocuments.size()));
        getVectorStore().addDocuments(embeddedDocuments); // VectorStore expects docs with embeddings
        notifyObservers("rag-vectorstore-added-documents", Map.of("count", embeddedDocuments.size()));
        notifyObservers("rag-adddocuments-stop", Map.of("processed_document_count", embeddedDocuments.size()));
    }
}

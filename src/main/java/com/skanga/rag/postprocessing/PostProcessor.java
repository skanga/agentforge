package com.skanga.rag.postprocessing;

import com.skanga.chat.messages.Message;
import com.skanga.rag.Document;
import java.util.List;

/**
 * Interface for document post-processors in a Retrieval Augmented Generation (RAG) pipeline.
 * Post-processors are applied to the list of {@link Document} objects retrieved from a
 * {@link com.skanga.rag.vectorstore.VectorStore} before these documents are used
 * to augment the context for an AI model.
 *
 * <p>Implementations can perform various tasks, such as:
 * <ul>
 *   <li>Re-ranking documents based on additional criteria (e.g., relevance scores from a
 *       separate reranker model like Cohere Rerank, or date).</li>
 *   <li>Filtering documents based on thresholds (e.g., minimum similarity score).</li>
 *   <li>Filtering documents based on metadata or content.</li>
 *   <li>Summarizing or transforming document content (though this might be complex for a simple post-processor).</li>
 *   <li>Ensuring diversity among the selected documents.</li>
 * </ul>
 * </p>
 * The {@code question} (original user query) is provided as it might be relevant for some
 * post-processing strategies (e.g., query-dependent reranking).
 */
@FunctionalInterface // Good candidate if process is the only core method.
public interface PostProcessor {

    /**
     * Processes a list of retrieved documents, potentially re-ranking, filtering, or transforming them.
     *
     * @param question  The original user query {@link Message} that led to the retrieval of these documents.
     *                  This can be used by the post-processor for context-aware processing.
     * @param documents The list of {@link Document} objects retrieved from the vector store.
     *                  These documents typically have their {@code score} field populated by the
     *                  similarity search.
     * @return A list of processed {@link Document} objects. This list might be shorter (due to filtering),
     *         re-ordered (due to re-ranking), or contain documents with modified scores or content.
     *         Should not be null; can be an empty list if all documents are filtered out.
     * @throws PostProcessorException if an error occurs during post-processing
     *                                (e.g., API error for an external reranker).
     */
    List<Document> process(Message question, List<Document> documents) throws PostProcessorException;
}

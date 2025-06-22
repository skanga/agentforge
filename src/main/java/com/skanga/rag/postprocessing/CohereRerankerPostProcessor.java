package com.skanga.rag.postprocessing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.chat.messages.Message;
import com.skanga.providers.HttpClientManager;
import com.skanga.rag.Document;
import com.skanga.rag.postprocessing.cohere.dto.CohereRerankRequest;
import com.skanga.rag.postprocessing.cohere.dto.CohereRerankResponse;
import com.skanga.rag.postprocessing.cohere.dto.CohereRerankResultItem;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap; // For deep copying metadata
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A {@link PostProcessor} that uses the Cohere Rerank API to re-rank a list of documents
 * based on their relevance to a given query.
 *
 * <p>This processor sends the query and document contents to the Cohere API and receives
 * a new ordering of documents along with relevance scores. The original documents are
 * then reordered and their scores updated based on Cohere's response.</p>
 *
 * <p><b>API Interaction:</b>
 * <ul>
 *   <li>Uses the JDK 11+ {@link HttpClient} for HTTP communication.</li>
 *   <li>Requires a Cohere API key for authentication.</li>
 *   <li>Allows specification of a Cohere reranking model (e.g., "rerank-english-v3.0").</li>
 *   <li>The number of documents returned after reranking can be controlled by the {@code topN} parameter.</li>
 * </ul>
 * </p>
 *
 * <p><b>Note:</b> The documents sent to Cohere are currently just their text content.
 * Any original metadata or embeddings are not sent to the reranking API but are preserved
 * and re-associated with the reranked documents based on their original index.</p>
 */
public class CohereRerankerPostProcessor implements PostProcessor {

    private final String apiKey;
    private final String modelName;
    /** The number of documents to return after reranking. */
    private final int topN;
    private final ObjectMapper objectMapper;
    private final String baseUri;

    /** Default Cohere Rerank model (English v3). */
    public static final String DEFAULT_COHERE_RERANK_MODEL = "rerank-english-v3.0";
    /** Default Cohere API URI for reranking. */
    public static final String DEFAULT_COHERE_RERANK_URI = "https://api.cohere.com/v1/rerank";


    /**
     * Constructs a CohereRerankerPostProcessor with full configuration.
     *
     * @param apiKey    Your Cohere API key. Must not be null.
     * @param modelName The Cohere reranking model to use (e.g., "rerank-english-v3.0").
     *                  If null, {@link #DEFAULT_COHERE_RERANK_MODEL} is used.
     * @param topN      The number of top documents to return after reranking. Must be positive.
     *                  If the number of input documents is less than topN, all reranked documents are returned.
     * @param baseUri   The base URI for the Cohere Rerank API. If null, {@link #DEFAULT_COHERE_RERANK_URI} is used.
     */
    public CohereRerankerPostProcessor(String apiKey, String modelName, int topN, String baseUri) {
        Objects.requireNonNull(apiKey, "Cohere API key cannot be null.");
        this.apiKey = apiKey;
        this.modelName = modelName != null ? modelName : DEFAULT_COHERE_RERANK_MODEL;
        if (topN <= 0) {
            System.err.println("Warning: CohereRerankerPostProcessor topN must be positive, defaulting to 3.");
            this.topN = 3;
        } else {
            this.topN = topN;
        }
        this.baseUri = baseUri != null ? baseUri : DEFAULT_COHERE_RERANK_URI;

        this.objectMapper = new ObjectMapper();
    }

    /**
     * Constructs with specified API key, model, and topN, using default URI.
     */
    public CohereRerankerPostProcessor(String apiKey, String modelName, int topN) {
        this(apiKey, modelName, topN, DEFAULT_COHERE_RERANK_URI);
    }

    /**
     * Constructs with specified API key and topN, using default model and URI.
     */
    public CohereRerankerPostProcessor(String apiKey, int topN) {
        this(apiKey, null, topN, DEFAULT_COHERE_RERANK_URI);
    }

    /**
     * Constructs with specified API key, using default model, topN (3), and URI.
     */
     public CohereRerankerPostProcessor(String apiKey) {
        this(apiKey, null, 3, DEFAULT_COHERE_RERANK_URI);
    }

    /**
     * {@inheritDoc}
     * <p>This implementation sends the document contents and the query to the Cohere Rerank API.
     * It then reorders the original list of {@link Document} objects based on the relevance scores
     * returned by Cohere and updates their scores. The number of documents returned is limited by
     * the {@code topN} parameter configured for this processor.</p>
     *
     * @throws PostProcessorException if the question content is invalid, if API call fails,
     *                                or if response parsing fails.
     */
    @Override
    public List<Document> process(Message question, List<Document> documents) throws PostProcessorException {
        Objects.requireNonNull(question, "Question message cannot be null for Cohere reranking.");
        Objects.requireNonNull(documents, "Input documents list cannot be null for Cohere reranking.");

        if (documents.isEmpty()) {
            return Collections.emptyList();
        }
        if (question.getContent() == null || !(question.getContent() instanceof String) || ((String)question.getContent()).trim().isEmpty()) {
            throw new PostProcessorException("Question content must be a non-empty string for Cohere reranking.");
        }
        String queryText = ((String) question.getContent()).trim();

        List<String> documentContents = documents.stream()
                .map(doc -> doc.getContent() != null ? doc.getContent() : "") // Send empty string for null content
                .collect(Collectors.toList());

        // Cohere's top_n in the request body is how many results the API will score and return.
        // This processor's topN field determines how many of those to ultimately keep.
        // We should request at least `this.topN` from Cohere if possible.
        int requestTopN = Math.min(this.topN, documentContents.size());
        // Cohere also has a max of 1000 documents per request. This should be handled if input `documents` is larger.
        // For now, assuming `documents.size()` is within Cohere's limits.

        CohereRerankRequest rerankRequest = new CohereRerankRequest(
                this.modelName,
                queryText,
                documentContents,
                requestTopN, // Ask Cohere to rerank and return up to this many.
                false,       // return_documents: false, we will map back by index.
                null         // max_chunks_per_doc: null for default.
        );

        String requestBodyJson;
        try {
            requestBodyJson = objectMapper.writeValueAsString(rerankRequest);
        } catch (JsonProcessingException e) {
            throw new PostProcessorException("Failed to serialize Cohere rerank request to JSON", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.baseUri))
                .header("Authorization", "Bearer " + this.apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();

        try {
            HttpResponse<String> httpResponse = HttpClientManager.getSharedClient().send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = httpResponse.body();

            if (httpResponse.statusCode() != 200) {
                throw new PostProcessorException("Cohere Rerank API request failed", httpResponse.statusCode(), responseBody);
            }

            CohereRerankResponse rerankResponse = objectMapper.readValue(responseBody, CohereRerankResponse.class);

            if (rerankResponse == null || rerankResponse.results() == null) {
                throw new PostProcessorException("Cohere Rerank response is empty or missing 'results'. Body: " + responseBody);
            }

            List<Document> rerankedDocuments = new ArrayList<>();
            for (CohereRerankResultItem resultItem : rerankResponse.results()) {
                if (resultItem.index() != null && resultItem.index() >= 0 && resultItem.index() < documents.size()) {
                    Document originalDoc = documents.get(resultItem.index());

                    // Create a new Document instance to avoid modifying the input document's score directly,
                    // as it might be used elsewhere or in other post-processors.
                    Document newRerankedDoc = new Document(originalDoc.getContent());
                    newRerankedDoc.setId(originalDoc.getId()); // Preserve original ID
                    newRerankedDoc.setSourceType(originalDoc.getSourceType());
                    newRerankedDoc.setSourceName(originalDoc.getSourceName());
                    // Deep copy metadata and embedding to ensure new Document is independent
                    if (originalDoc.getMetadata() != null) {
                        newRerankedDoc.setMetadata(new HashMap<>(originalDoc.getMetadata()));
                    }
                    if (originalDoc.getEmbedding() != null) {
                        newRerankedDoc.setEmbedding(new ArrayList<>(originalDoc.getEmbedding()));
                    }

                    if (resultItem.relevanceScore() != null) {
                        newRerankedDoc.setScore(resultItem.relevanceScore().floatValue());
                    }
                    rerankedDocuments.add(newRerankedDoc);
                } else {
                    System.err.println("Warning: Cohere Reranker returned an invalid or out-of-bounds index: " +
                                       (resultItem.index() != null ? resultItem.index() : "null") +
                                       " for document list of size " + documents.size());
                }
            }
            // The results from Cohere API are already sorted by relevance_score (descending).
            // The `topN` in the request to Cohere already limited the number of results from their side.
            // Our `this.topN` (used as `requestTopN`) ensures we don't ask for more than we want.
            return rerankedDocuments;

        } catch (JsonProcessingException e) { // Error deserializing Cohere's response
            throw new PostProcessorException("Failed to deserialize Cohere Rerank response from JSON: " + e.getMessage(), e);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // Preserve interrupt status
            }
            throw new PostProcessorException("Error during Cohere Rerank API call: " + e.getMessage(), e);
        }
    }
}

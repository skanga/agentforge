package com.skanga.rag.vectorstore.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
// import co.elastic.clients.elasticsearch.indices.PutMappingRequest; // For updating mapping if needed
// import co.elastic.clients.elasticsearch.indices.GetMappingRequest; // For getting mapping if needed
// import co.elastic.clients.elasticsearch.indices.GetMappingResponse; // For getting mapping if needed
// import co.elastic.clients.json.JsonData; // Not directly used if passing Map for document
import co.elastic.clients.json.jackson.JacksonJsonpMapper; // To get ObjectMapper from ES client
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.rag.Document;
import com.skanga.rag.vectorstore.VectorStore;
import com.skanga.rag.vectorstore.VectorStoreException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
// import java.util.stream.Collectors; // Not strictly needed in current version

/**
 * A {@link VectorStore} implementation for Elasticsearch.
 * This class uses the official Elasticsearch Java client to interact with an Elasticsearch cluster.
 *
 * p><b>Features:</b>
 * <ul>
 *   <li>Manages documents within a specified Elasticsearch index.</li>
 *   <li>Automatically creates the index with a suitable mapping for vector search
 *       (using `dense_vector` field for embeddings with cosine similarity) if it doesn't exist,
 *       based on the first document added.</li>
 *   <li>Adds documents in bulk using Elasticsearch's Bulk API.</li>
 *   <li>Performs similarity searches using Elasticsearch's k-Nearest Neighbor (kNN) search API.</li>
 * </ul>
 * </p>
 *
 * <p><b>Prerequisites:</b>
 * <ul>
 *   <li>An Elasticsearch cluster (version supporting `dense_vector` and kNN search, typically 7.3+ or 8.x+).</li>
 *   <li>The Elasticsearch Java client configured in the application's dependencies (see POM comment).</li>
 * </ul>
 * </p>
 *
 * <p><b>Mapping and Dimensions:</b>
 * The dimension of the `dense_vector` field for embeddings is determined from the
 * first document added to the store. Subsequent documents must have embeddings of the
 * same dimension. Changing dimensions typically requires reindexing in Elasticsearch.
 * </p>
 *
 * <p><b>Metadata Filtering:</b>
 * Basic support for filtering is provided via the `withFilters(Map)` method. The actual
 * conversion of this filter map into Elasticsearch Query DSL needs to be implemented
 * within the `similaritySearch` method if complex filtering is required. For now,
 * it's a placeholder for future enhancement.
 * </p>
 */
public class ElasticsearchVectorStore implements VectorStore {

    private final ElasticsearchClient elasticsearchClient;
    private final String indexName;
    private final int defaultTopK; // Currently not used as k is always passed to search
    private Map<String, Object> filters; // For Elasticsearch filter DSL (structure TBD)
    private final ObjectMapper objectMapper; // For document source mapping if needed, usually ES client handles

    /** Stores the dimension of the vectors in this index, discovered from the first document. */
    private int vectorDimension = 0;
    /** Flag to ensure index mapping check/creation happens only once per instance. */
    private boolean mappingCheckedAndSet = false;

    // Standardized field names for Elasticsearch mapping
    private static final String MAPPING_FIELD_EMBEDDING = "embedding";
    private static final String MAPPING_FIELD_CONTENT = "content";
    private static final String MAPPING_FIELD_SOURCE_TYPE = "sourceType";
    private static final String MAPPING_FIELD_SOURCE_NAME = "sourceName";

    /** Default value for K if constructor doesn't specify. */
    private static final int DEFAULT_K_ELASTIC = 5;


    /**
     * Constructs an ElasticsearchVectorStore.
     *
     * @param elasticsearchClient An initialized {@link ElasticsearchClient}. Must not be null.
     * @param indexName           The name of the Elasticsearch index to use. Must not be null.
     * @param defaultTopK         A default value for 'k' (top results). Must be positive.
     */
    public ElasticsearchVectorStore(ElasticsearchClient elasticsearchClient, String indexName, int defaultTopK) {
        Objects.requireNonNull(elasticsearchClient, "ElasticsearchClient cannot be null.");
        Objects.requireNonNull(indexName, "Index name cannot be null.");
        if (defaultTopK <= 0) {
            throw new IllegalArgumentException("defaultTopK must be positive.");
        }

        this.elasticsearchClient = elasticsearchClient;
        this.indexName = indexName;
        this.defaultTopK = defaultTopK;
        this.filters = new HashMap<>();

        // Attempt to use ObjectMapper from the ES client if it's Jackson-based, otherwise create new.
        if (elasticsearchClient._transport().jsonpMapper() instanceof JacksonJsonpMapper) {
            this.objectMapper = ((JacksonJsonpMapper) elasticsearchClient._transport().jsonpMapper()).objectMapper();
        } else {
            this.objectMapper = new ObjectMapper();
        }
    }

    /**
     * Constructs an ElasticsearchVectorStore with a default top-K value.
     * @param elasticsearchClient An initialized {@link ElasticsearchClient}.
     * @param indexName           The name of the Elasticsearch index.
     */
    public ElasticsearchVectorStore(ElasticsearchClient elasticsearchClient, String indexName) {
        this(elasticsearchClient, indexName, DEFAULT_K_ELASTIC);
    }


    /**
     * Checks if the Elasticsearch index exists. If not, it creates the index with a predefined mapping
     * suitable for vector search (dense_vector for embeddings with cosine similarity).
     * The dimension of the dense_vector is determined from the first document's embedding.
     * This method is synchronized and designed to run once.
     *
     * @param firstDocument The first document being added, used to determine embedding dimension.
     * @throws VectorStoreException if checking/creating the index or mapping fails, or if the first document has no embedding.
     */
    private synchronized void checkAndEnsureIndexMapping(Document firstDocument) throws VectorStoreException {
        if (mappingCheckedAndSet) {
            return;
        }
        Objects.requireNonNull(firstDocument, "First document cannot be null for mapping check.");
        if (firstDocument.getEmbedding() == null || firstDocument.getEmbedding().isEmpty()) {
            throw new VectorStoreException("First document for mapping check must have a valid (non-empty) embedding to determine dimension. Doc ID: " + firstDocument.getId());
        }
        this.vectorDimension = firstDocument.getEmbedding().size();
        if (this.vectorDimension == 0) { // Should be caught by isEmpty, but defensive
             throw new VectorStoreException("Embedding dimension for first document is 0. Cannot create mapping. Doc ID: " + firstDocument.getId());
        }


        try {
            BooleanResponse existsResponse = elasticsearchClient.indices().exists(new ExistsRequest.Builder().index(this.indexName).build());

            if (!existsResponse.value()) {
                // Index does not exist, create it with mapping
                CreateIndexRequest.Builder createIndexBuilder = new CreateIndexRequest.Builder().index(this.indexName);
                createIndexBuilder.mappings(m -> m
                    .properties(MAPPING_FIELD_EMBEDDING, p -> p
                        .denseVector(dv -> dv
                            .dims(this.vectorDimension)
                            .index(true) // Enable indexing for kNN search
                            .similarity(DenseVectorSimilarity.Cosine) // Common choice for semantic similarity
                        )
                    )
                    .properties(MAPPING_FIELD_CONTENT, p -> p.text(t -> t)) // Standard text field
                    .properties(MAPPING_FIELD_SOURCE_TYPE, p -> p.keyword(k -> k)) // Keyword for exact matches/aggregations
                    .properties(MAPPING_FIELD_SOURCE_NAME, p -> p.keyword(k -> k))
                    // Other metadata fields from Document.metadata will be dynamically mapped by Elasticsearch by default.
                    // If specific mapping is needed for metadata fields (e.g., date, number), it should be added here.
                );
                elasticsearchClient.indices().create(createIndexBuilder.build());
                System.out.println("Elasticsearch index '" + this.indexName + "' created with mapping for 'embedding' (dims: " + this.vectorDimension + ", similarity: cosine).");
            } else {
                // Index exists. Optionally, verify existing mapping here.
                // GetFieldMappingRequest or GetMappingRequest could be used.
                // Note: Modifying 'dims' of an existing 'dense_vector' field is not allowed.
                // For simplicity, we assume if index exists, its mapping is compatible or managed externally.
                System.out.println("Elasticsearch index '" + this.indexName + "' already exists. Assuming compatible mapping.");
                // TODO: Consider fetching existing mapping to confirm vectorDimension if not set yet.
                // This is important if the class instance is new but the index pre-exists.
                // For now, vectorDimension is set from the first doc added to *this instance*.
            }
            mappingCheckedAndSet = true;
        } catch (IOException e) { // Covers ES client communication errors
            throw new VectorStoreException("Failed to check or create Elasticsearch index/mapping for '" + this.indexName + "': " + e.getMessage(), e);
        } catch (Exception e) { // Catch other potential ES client exceptions
             throw new VectorStoreException("Unexpected error during Elasticsearch index setup for '" + this.indexName + "': " + e.getMessage(), e);
        }
    }


    @Override
    public void addDocument(Document document) throws VectorStoreException {
        addDocuments(Collections.singletonList(document));
    }

    @Override
    public void addDocuments(List<Document> documents) throws VectorStoreException {
        Objects.requireNonNull(documents, "Documents list cannot be null.");
        if (documents.isEmpty()) {
            return;
        }
        // Ensure mapping is checked and vectorDimension is set based on the first valid document.
        // This handles the case where the store is new or vectorDimension hasn't been initialized.
        if (!mappingCheckedAndSet) {
            Document firstValidDocForMapping = null;
            for (Document doc : documents) {
                if (doc != null && doc.getEmbedding() != null && !doc.getEmbedding().isEmpty()) {
                    firstValidDocForMapping = doc;
                    break;
                }
            }
            if (firstValidDocForMapping == null) {
                throw new VectorStoreException("No document with valid embedding found in the batch to establish mapping.");
            }
            checkAndEnsureIndexMapping(firstValidDocForMapping);
        }

        BulkRequest.Builder br = new BulkRequest.Builder();
        for (Document doc : documents) {
            Objects.requireNonNull(doc, "Document in list cannot be null.");
            if (doc.getEmbedding() == null || doc.getEmbedding().isEmpty()) {
                throw new VectorStoreException("Document embedding cannot be null or empty for Elasticsearch. Doc ID: " + doc.getId());
            }
            // Validate dimension consistency after mapping is set and vectorDimension is known
            if (this.vectorDimension > 0 && doc.getEmbedding().size() != this.vectorDimension) {
                 throw new VectorStoreException("Document embedding dimension " + doc.getEmbedding().size() +
                                                " does not match established index dimension " + this.vectorDimension + ". Doc ID: " + doc.getId());
            }

            Map<String, Object> sourceMap = new HashMap<>();
            sourceMap.put(MAPPING_FIELD_EMBEDDING, doc.getEmbedding());
            sourceMap.put(MAPPING_FIELD_CONTENT, doc.getContent());
            if(doc.getSourceType() != null) sourceMap.put(MAPPING_FIELD_SOURCE_TYPE, doc.getSourceType());
            if(doc.getSourceName() != null) sourceMap.put(MAPPING_FIELD_SOURCE_NAME, doc.getSourceName());

            if (doc.getMetadata() != null && !doc.getMetadata().isEmpty()) {
                // Merge document's metadata. Ensure keys don't clash with predefined fields,
                // or handle by prefixing/nesting if necessary.
                doc.getMetadata().forEach((key, value) -> {
                    if (!sourceMap.containsKey(key)) { // Avoid overwriting main fields if metadata had same keys
                        sourceMap.put(key, value);
                    } else {
                        System.err.println("Warning: Metadata key '" + key + "' for doc ID '" + doc.getId() +
                                           "' conflicts with a main field and was not added to ES source.");
                    }
                });
            }

            final Map<String, Object> finalSourceMap = sourceMap;
            br.operations(op -> op
                .index(idx -> idx
                    .index(this.indexName)
                    .id(doc.getId()) // Use document's ID as Elasticsearch document ID
                    .document(finalSourceMap)
                )
            );
        }

        try {
            BulkResponse result = elasticsearchClient.bulk(br.build());
            if (result.errors()) {
                StringBuilder errorMessages = new StringBuilder("Bulk upsert to Elasticsearch encountered errors: ");
                for (BulkResponseItem item : result.items()) {
                    if (item.error() != null) {
                        errorMessages.append("\nID ").append(item.id()).append(" (Index: ").append(item.index()).append("): Type: ").append(item.error().type()).append(" Reason: ").append(item.error().reason());
                    }
                }
                throw new VectorStoreException(errorMessages.toString());
            }
            // Optional: Refresh index if immediate searchability after add is critical
            // This has performance implications for frequent writes.
            // elasticsearchClient.indices().refresh(r -> r.index(this.indexName));
        } catch (IOException e) { // Covers ES client communication errors
            throw new VectorStoreException("Failed to bulk upsert documents to Elasticsearch index '" + this.indexName + "': " + e.getMessage(), e);
        } catch (Exception e) { // Catch other potential ES client exceptions
             throw new VectorStoreException("Unexpected error during Elasticsearch bulk upsert for '" + this.indexName + "': " + e.getMessage(), e);
        }
    }

    @Override
    public List<Document> similaritySearch(List<Double> queryEmbedding, int k) throws VectorStoreException {
        Objects.requireNonNull(queryEmbedding, "Query embedding cannot be null for similarity search.");
        if (k <= 0) {
            throw new IllegalArgumentException("Number of results to return (k) must be positive.");
        }
        if (!mappingCheckedAndSet || this.vectorDimension == 0) {
            // Attempt to check mapping if not done. This might happen if store is new and queried before add.
            // However, checkAndEnsureIndexMapping needs a document to infer dimension.
            // So, it's better to enforce that addDocuments (which calls checkAndEnsure...) happens first.
            throw new VectorStoreException("Index mapping not yet established or vector dimension unknown. " +
                                           "Ensure at least one document has been added before searching.");
        }
         if (queryEmbedding.size() != this.vectorDimension) {
            throw new VectorStoreException("Query embedding dimension " + queryEmbedding.size() +
                                           " does not match index dimension " + this.vectorDimension + ".");
        }

        // Convert Double to Float for Elasticsearch client
        List<Float> floatEmbedding = queryEmbedding.stream()
                .map(Double::floatValue)
                .collect(Collectors.toList());

        SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder()
            .index(this.indexName)
            .knn(knn -> {
                knn.field(MAPPING_FIELD_EMBEDDING)
                   .queryVector(floatEmbedding)
                   .k(k)
                   .numCandidates(Math.max(50, k * 5)); // num_candidates should be >= k, often larger for HNSW

                // Placeholder for filter conversion from this.filters
                // if (this.filters != null && !this.filters.isEmpty()) {
                //    // Convert this.filters map to Elasticsearch Query DSL object
                //    // Example: Query esQueryFilter = convertMapToEsQuery(this.filters);
                //    // knn.filter(esQueryFilter);
                //    System.err.println("Warning: ElasticsearchVectorStore filters are set but not yet implemented for kNN query.");
                // }
                return knn;
            }
            );

        try {
            // Use Map.class for _source for flexibility. A specific DTO could be created.
            SearchResponse<Map> response = elasticsearchClient.search(searchRequestBuilder.build(), Map.class);

            List<Document> resultDocuments = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> sourceMap = hit.source();
                if (sourceMap == null) continue;

                String content = (String) sourceMap.get(MAPPING_FIELD_CONTENT);
                // If content is vital, decide how to handle if it's missing. For now, default to empty.
                content = (content == null) ? "" : content;

                Document doc = new Document(content);
                doc.setId(hit.id()); // Elasticsearch document ID
                if (hit.score() != null) {
                    // Elasticsearch kNN search score is a similarity score (higher is better).
                    // For cosine similarity, it's typically 0.5 to 1.0 (or 1.0 to 2.0 if not normalized, but usually it's (1+cos_sim)/2 or similar).
                    // If 'cosine' similarity is used in mapping, score is (1 + cos_sim) / 2. To get raw cos_sim: (score * 2) - 1
                    // Or, if it's already a direct similarity measure like dot_product, it can be used as is.
                    // For "cosine" in ES, score = (1 + cosineSimilarity) / 2. So, higher is better, max 1.0.
                    // We can directly use this score or convert it back if needed.
                    // Let's assume hit.score() is directly usable as a relevance score [0,1] for cosine.
                    doc.setScore(hit.score().floatValue());
                }

                doc.setSourceType((String) sourceMap.get(MAPPING_FIELD_SOURCE_TYPE));
                doc.setSourceName((String) sourceMap.get(MAPPING_FIELD_SOURCE_NAME));

                Map<String, Object> originalMetadata = new HashMap<>();
                for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
                    // Exclude known, top-level mapped fields from being duplicated in metadata map
                    if (!List.of(MAPPING_FIELD_EMBEDDING, MAPPING_FIELD_CONTENT, MAPPING_FIELD_SOURCE_TYPE, MAPPING_FIELD_SOURCE_NAME).contains(entry.getKey())) {
                        originalMetadata.put(entry.getKey(), entry.getValue());
                    }
                }
                doc.setMetadata(originalMetadata);

                // Embedding itself is usually not returned in _source unless explicitly configured in mapping.
                // If needed, it could be fetched or mapping adjusted. For RAG, often not needed in search result objects.

                resultDocuments.add(doc);
            }
            return resultDocuments;

        } catch (IOException e) { // Covers ES client communication errors
            throw new VectorStoreException("Failed to perform similarity search on Elasticsearch index '" + this.indexName + "': " + e.getMessage(), e);
        } catch (Exception e) { // Catch other potential ES client exceptions
             throw new VectorStoreException("Unexpected error during Elasticsearch similarity search for '" + this.indexName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Fluent setter for metadata filters to be applied during similarity search.
     * The structure of the filterMap should conform to a simplified structure that
     * can be translated into Elasticsearch Query DSL, or be the Query DSL itself as a Map.
     * Note: The actual conversion and application of these filters in `similaritySearch`
     * is currently a placeholder and needs full implementation.
     *
     * @param filters A map representing the filter criteria.
     * @return This {@code ElasticsearchVectorStore} instance.
     */
    public ElasticsearchVectorStore withFilters(Map<String, Object> filters) {
        this.filters = (filters == null) ? new HashMap<>() : new HashMap<>(filters);
        return this;
    }

    /**
     * Clears any previously set filters.
     */
    public void clearFilters() {
        this.filters.clear();
    }
}

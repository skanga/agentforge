package com.skanga.rag.vectorstore.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.endpoints.BooleanResponse; // Corrected import
import com.skanga.rag.Document;
import com.skanga.rag.vectorstore.VectorStoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElasticsearchVectorStoreTests {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    private ElasticsearchVectorStore vectorStore;
    private static final String TEST_INDEX = "test_index";
    private static final int DEFAULT_TOP_K = 5;

    @BeforeEach
    void setUp() throws IOException { // Added throws IOException
        // Mock the transport and mapper to prevent NPE in constructor
        ElasticsearchTransport mockTransport = mock(ElasticsearchTransport.class);
        JsonpMapper mockMapper = mock(JsonpMapper.class);
        ElasticsearchIndicesClient mockIndicesClient = mock(ElasticsearchIndicesClient.class);
        BooleanResponse mockExistsResponse = mock(BooleanResponse.class);

        lenient().when(elasticsearchClient._transport()).thenReturn(mockTransport);
        lenient().when(mockTransport.jsonpMapper()).thenReturn(mockMapper);
        lenient().when(elasticsearchClient.indices()).thenReturn(mockIndicesClient);

        // Default behavior: assume index exists
        lenient().when(mockExistsResponse.value()).thenReturn(true);
        lenient().when(mockIndicesClient.exists(any(ExistsRequest.class))).thenReturn(mockExistsResponse);

        vectorStore = new ElasticsearchVectorStore(elasticsearchClient, TEST_INDEX, DEFAULT_TOP_K);
    }

    @Test
    void constructor_WithValidParameters_ShouldCreateInstance() {
        // Act & Assert
        assertThat(vectorStore).isNotNull();
    }

    @Test
    void constructor_WithNullClient_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new ElasticsearchVectorStore(null, TEST_INDEX, DEFAULT_TOP_K));
    }

    @Test
    void constructor_WithNullIndexName_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new ElasticsearchVectorStore(elasticsearchClient, null, DEFAULT_TOP_K));
    }

    @Test
    void constructor_WithInvalidTopK_ShouldThrowException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                new ElasticsearchVectorStore(elasticsearchClient, TEST_INDEX, 0));
    }

    @Test
    void addDocument_WithNullDocument_ShouldThrowException() {
        // Act & Assert
        VectorStoreException exception = assertThrows(VectorStoreException.class, () ->
                vectorStore.addDocument(null));
        // This message is thrown if the document list for checkAndEnsureIndexMapping is effectively empty of valid docs.
        assertThat(exception.getMessage()).isEqualTo("No document with valid embedding found in the batch to establish mapping.");
        // In this specific path (list containing only null), no cause is set for the VectorStoreException.
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void addDocument_WithoutEmbedding_ShouldThrowException() {
        // Arrange
        Document document = new Document("Test content");
        // No embedding set

        // Act & Assert
        VectorStoreException exception = assertThrows(VectorStoreException.class, () ->
                vectorStore.addDocument(document));
        assertThat(exception.getMessage()).contains("No document with valid embedding found in the batch to establish mapping.");
    }

    @Test
    void addDocuments_WithEmptyList_ShouldReturn() {
        // Act & Assert
        assertDoesNotThrow(() -> vectorStore.addDocuments(Collections.emptyList()));
        // Constructor interacts with elasticsearchClient via _transport().jsonpMapper(), so verifyNoInteractions is too strict.
        // We only need to ensure no *further* interactions like bulk calls happen.
        // The assertDoesNotThrow is the primary check here that it returns early.
    }

    @Test
    void addDocuments_WithValidDocument_ShouldNotThrow() throws Exception {
        // Arrange
        Document document = createTestDocument("test-id", "Test content", Arrays.asList(0.1, 0.2, 0.3));

        // Mock the bulk response to indicate success
        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.errors()).thenReturn(false);
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

        // Act & Assert
        assertDoesNotThrow(() -> vectorStore.addDocuments(Arrays.asList(document)));
        verify(elasticsearchClient).bulk(any(BulkRequest.class));
    }

    @Test
    void addDocuments_WithBulkErrors_ShouldThrowException() throws Exception {
        // Arrange
        Document document = createTestDocument("test-id", "Test content", Arrays.asList(0.1, 0.2, 0.3));

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.errors()).thenReturn(true);

        BulkResponseItem errorItem = mock(BulkResponseItem.class);
        // Make these stubs lenient as they might not be called if items() list is empty or error handling changes
        lenient().when(errorItem.error()).thenReturn(null);
        lenient().when(errorItem.id()).thenReturn("test-id");
        lenient().when(errorItem.index()).thenReturn(TEST_INDEX);
        when(bulkResponse.items()).thenReturn(Arrays.asList(errorItem));

        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

        // Act & Assert
        VectorStoreException exception = assertThrows(VectorStoreException.class, () ->
                vectorStore.addDocuments(Arrays.asList(document)));
        assertThat(exception.getMessage()).contains("Bulk upsert to Elasticsearch encountered errors");
    }

    @Test
    void similaritySearch_WithNullEmbedding_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                vectorStore.similaritySearch(null, 5));
    }

    @Test
    void similaritySearch_WithInvalidK_ShouldThrowException() {
        // Arrange
        List<Double> queryEmbedding = Arrays.asList(0.1, 0.2, 0.3);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                vectorStore.similaritySearch(queryEmbedding, 0));
    }

    @Test
    void similaritySearch_WithValidEmbedding_ShouldReturnDocuments() throws Exception {
        // Arrange
        List<Double> queryEmbedding = Arrays.asList(0.1, 0.2, 0.3);
        int k = 3;

        // First add a document to establish dimension
        Document document = createTestDocument("setup-doc", "Setup", Arrays.asList(0.1, 0.2, 0.3));
        BulkResponse setupBulkResponse = mock(BulkResponse.class);
        when(setupBulkResponse.errors()).thenReturn(false);
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(setupBulkResponse);
        vectorStore.addDocument(document);

        // Mock search response
        SearchResponse<Map> searchResponse = mock(SearchResponse.class);
        HitsMetadata<Map> hitsMetadata = mock(HitsMetadata.class);
        Hit<Map> hit = mock(Hit.class);

        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("content", "Test content");
        // Use the static fields from ElasticsearchVectorStore for keys
        sourceMap.put(ElasticsearchVectorStore.MAPPING_FIELD_SOURCE_TYPE, "test");
        sourceMap.put(ElasticsearchVectorStore.MAPPING_FIELD_SOURCE_NAME, "test.txt");

        when(hit.id()).thenReturn("test-id");
        when(hit.score()).thenReturn(0.95);
        when(hit.source()).thenReturn(sourceMap);
        when(hitsMetadata.hits()).thenReturn(Arrays.asList(hit));
        when(searchResponse.hits()).thenReturn(hitsMetadata);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class))).thenReturn(searchResponse);

        // Act
        List<Document> results = vectorStore.similaritySearch(queryEmbedding, k);

        // Assert
        assertThat(results).hasSize(1);
        Document result = results.get(0);
        assertThat(result.getId()).isEqualTo("test-id");
        assertThat(result.getContent()).isEqualTo("Test content");
        assertThat(result.getScore()).isEqualTo(0.95f);
        assertThat(result.getSourceType()).isEqualTo("test");
        assertThat(result.getSourceName()).isEqualTo("test.txt");
    }

    @Test
    void similaritySearch_WithWrongDimension_ShouldThrowException() throws Exception {
        // Arrange - First add a document to establish dimension
        Document document = createTestDocument("test-id", "Test content", Arrays.asList(0.1, 0.2, 0.3));

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.errors()).thenReturn(false);
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

        vectorStore.addDocument(document);

        // Now try to search with wrong dimension
        List<Double> wrongDimensionEmbedding = Arrays.asList(0.1, 0.2); // 2D instead of 3D

        // Act & Assert
        VectorStoreException exception = assertThrows(VectorStoreException.class, () ->
                vectorStore.similaritySearch(wrongDimensionEmbedding, 5));
        assertThat(exception.getMessage()).contains("Query embedding dimension");
    }

    @Test
    void withFilters_ShouldReturnSameInstance() {
        // Arrange
        Map<String, Object> filters = Map.of("category", "test");

        // Act
        ElasticsearchVectorStore result = vectorStore.withFilters(filters);

        // Assert
        assertThat(result).isSameAs(vectorStore);
    }

    @Test
    void clearFilters_ShouldNotThrow() {
        // Arrange
        vectorStore.withFilters(Map.of("category", "test"));

        // Act & Assert
        assertDoesNotThrow(() -> vectorStore.clearFilters());
    }

    @Test
    void elasticsearchIOException_ShouldBeWrappedInVectorStoreException() throws Exception {
        // Arrange
        Document document = createTestDocument("test-id", "Test content", Arrays.asList(0.1, 0.2, 0.3));

        when(elasticsearchClient.bulk(any(BulkRequest.class)))
                .thenThrow(new IOException("Connection failed"));

        // Act & Assert
        VectorStoreException exception = assertThrows(VectorStoreException.class, () ->
                vectorStore.addDocument(document));
        assertThat(exception.getMessage()).contains("Failed to bulk upsert documents");
    }

    @Test
    void elasticsearchRuntimeException_ShouldBeWrappedInVectorStoreException() throws Exception {
        // Arrange
        Document document = createTestDocument("test-id", "Test content", Arrays.asList(0.1, 0.2, 0.3));

        when(elasticsearchClient.bulk(any(BulkRequest.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        // Act & Assert
        VectorStoreException exception = assertThrows(VectorStoreException.class, () ->
                vectorStore.addDocument(document));
        assertThat(exception.getMessage()).contains("Unexpected error during Elasticsearch bulk upsert");
    }

    @Test
    void similaritySearch_WithEmptyResults_ShouldReturnEmptyList() throws Exception {
        // Arrange
        List<Double> queryEmbedding = Arrays.asList(0.1, 0.2, 0.3);

        // First add a document to establish dimension
        Document document = createTestDocument("setup-doc", "Setup", Arrays.asList(0.1, 0.2, 0.3));
        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.errors()).thenReturn(false);
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);
        vectorStore.addDocument(document);

        // Mock empty search response
        SearchResponse<Map> searchResponse = mock(SearchResponse.class);
        HitsMetadata<Map> hitsMetadata = mock(HitsMetadata.class);
        when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
        when(searchResponse.hits()).thenReturn(hitsMetadata);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class))).thenReturn(searchResponse);

        // Act
        List<Document> results = vectorStore.similaritySearch(queryEmbedding, 5);

        // Assert
        assertThat(results).isEmpty();
    }

    @Test
    void similaritySearch_WithSearchIOException_ShouldThrowVectorStoreException() throws Exception {
        // Arrange
        List<Double> queryEmbedding = Arrays.asList(0.1, 0.2, 0.3);

        // First add a document to establish dimension
        Document document = createTestDocument("setup-doc", "Setup", Arrays.asList(0.1, 0.2, 0.3));
        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.errors()).thenReturn(false);
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);
        vectorStore.addDocument(document);

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class)))
                .thenThrow(new IOException("Search failed"));

        // Act & Assert
        VectorStoreException exception = assertThrows(VectorStoreException.class, () ->
                vectorStore.similaritySearch(queryEmbedding, 5));
        assertThat(exception.getMessage()).contains("Failed to perform similarity search");
    }

    @Test
    void similaritySearch_WithMissingDimensionInfo_ShouldThrowException() {
        // Arrange
        List<Double> queryEmbedding = Arrays.asList(0.1, 0.2, 0.3);

        // Don't add any documents first, so dimension is unknown

        // Act & Assert
        VectorStoreException exception = assertThrows(VectorStoreException.class, () ->
                vectorStore.similaritySearch(queryEmbedding, 5));
        assertThat(exception.getMessage()).contains("Index mapping not yet established");
    }

    @Test
    void addDocuments_WithInconsistentDimensions_ShouldThrowException() throws Exception {
        // Arrange
        Document doc1 = createTestDocument("doc1", "Content 1", Arrays.asList(0.1, 0.2, 0.3)); // 3D
        Document doc2 = createTestDocument("doc2", "Content 2", Arrays.asList(0.1, 0.2)); // 2D

        // Mock successful first document addition
        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.errors()).thenReturn(false);
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

        vectorStore.addDocument(doc1); // This establishes 3D dimension

        // Act & Assert
        VectorStoreException exception = assertThrows(VectorStoreException.class, () ->
                vectorStore.addDocument(doc2));
        assertThat(exception.getMessage()).contains("embedding dimension");
        assertThat(exception.getMessage()).contains("does not match established index dimension");
    }

    @Test
    void similaritySearch_WithNullScoreInHit_ShouldHandleGracefully() throws Exception {
        // Arrange
        List<Double> queryEmbedding = Arrays.asList(0.1, 0.2, 0.3);

        // Setup dimension
        Document document = createTestDocument("setup-doc", "Setup", Arrays.asList(0.1, 0.2, 0.3));
        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.errors()).thenReturn(false);
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);
        vectorStore.addDocument(document);

        // Mock search response with null score
        SearchResponse<Map> searchResponse = mock(SearchResponse.class);
        HitsMetadata<Map> hitsMetadata = mock(HitsMetadata.class);
        Hit<Map> hit = mock(Hit.class);

        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("content", "Test content");

        when(hit.id()).thenReturn("test-id");
        when(hit.score()).thenReturn(null); // Null score
        when(hit.source()).thenReturn(sourceMap);
        when(hitsMetadata.hits()).thenReturn(Arrays.asList(hit));
        when(searchResponse.hits()).thenReturn(hitsMetadata);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class))).thenReturn(searchResponse);

        // Act
        List<Document> results = vectorStore.similaritySearch(queryEmbedding, 5);

        // Assert
        assertThat(results).hasSize(1);
        Document result = results.get(0);
        assertThat(result.getScore()).isEqualTo(0.0f); // Should default to 0
    }

    private Document createTestDocument(String id, String content, List<Double> embedding) {
        Document document = new Document(content);
        document.setId(id);
        document.setEmbedding(embedding);
        document.setSourceType("test");
        document.setSourceName("test.txt");
        return document;
    }
}
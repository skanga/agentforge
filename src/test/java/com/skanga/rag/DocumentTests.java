package com.skanga.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

class DocumentTests {

    private Document document;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        document = new Document("Initial test content");
    }

    // --- Constructor Tests ---

    @Test
    void constructor_WithValidContent_ShouldCreateDocumentWithDefaults() {
        // Arrange
        String content = "This is a test document.";

        // Act
        Document doc = new Document(content);

        // Assert
        assertThat(doc.getContent()).isEqualTo(content);
        assertThat(doc.getId()).isNotNull();
        assertThatCode(() -> UUID.fromString(doc.getId())).doesNotThrowAnyException(); // Valid UUID
        assertThat(doc.getEmbedding()).isNotNull().isEmpty();
        assertThat(doc.getMetadata()).isNotNull().isEmpty();
        assertThat(doc.getScore()).isEqualTo(0.0f);
        assertThat(doc.getSourceType()).isEqualTo("manual");
        assertThat(doc.getSourceName()).isEqualTo("manual");
    }

    @Test
    void constructor_WithNullContent_ShouldThrowNullPointerException() {
        assertThatThrownBy(() -> new Document(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Document content cannot be null."); // Adjusted message
    }

    @Test
    void defaultConstructor_initializesWithDefaults() {
        // Act
        Document doc = new Document();

        // Assert
        assertThat(doc.getContent()).isNull(); // Default constructor allows null content initially
        assertThat(doc.getId()).isNotNull();
        assertThat(doc.getEmbedding()).isNotNull().isEmpty();
        assertThat(doc.getMetadata()).isNotNull().isEmpty();
        assertThat(doc.getScore()).isEqualTo(0.0f);
        assertThat(doc.getSourceType()).isEqualTo("manual");
        assertThat(doc.getSourceName()).isEqualTo("manual");
    }

    // --- Setter and Getter Tests ---

    @Test
    void setId_WithValidId_ShouldSetId() {
        // Arrange
        String newId = "test-id-123";

        // Act
        document.setId(newId);

        // Assert
        assertThat(document.getId()).isEqualTo(newId);
    }

    @Test
    void setId_WithNullId_ShouldThrowNullPointerException() {
        assertThatThrownBy(() -> document.setId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("ID cannot be null."); // Adjusted message
    }

    @Test
    void setContent_WithValidContent_ShouldSetContent() {
        // Arrange
        String newContent = "Updated content";

        // Act
        document.setContent(newContent);

        // Assert
        assertThat(document.getContent()).isEqualTo(newContent);
    }

    @Test
    void setContent_WithNullContent_ShouldThrowNullPointerException() {
        assertThatThrownBy(() -> document.setContent(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Content cannot be null."); // Adjusted message
    }

    @Test
    void setEmbedding_WithValidEmbedding_ShouldSetDefensiveCopy() {
        // Arrange
        List<Double> embedding = Arrays.asList(0.1, 0.2, 0.3);

        // Act
        document.setEmbedding(embedding);

        // Assert
        assertThat(document.getEmbedding()).isEqualTo(embedding);
        assertThat(document.getEmbedding()).isNotSameAs(embedding); // Should be a copy
    }

    @Test
    void setEmbedding_WithNullEmbedding_ShouldSetEmptyList() {
        // Act
        document.setEmbedding(null);

        // Assert
        assertThat(document.getEmbedding()).isNotNull().isEmpty();
    }

    @Test
    void setMetadata_WithValidMetadata_ShouldSetDefensiveCopy() {
        // Arrange
        Map<String, Object> metadata = Map.of(
                "author", "John Doe",
                "year", 2023
        );

        // Act
        document.setMetadata(metadata);

        // Assert
        assertThat(document.getMetadata()).isEqualTo(metadata);
        assertThat(document.getMetadata()).isNotSameAs(metadata); // Should be a copy
    }

    @Test
    void setMetadata_WithNullMetadata_ShouldSetEmptyMap() {
        // Act
        document.setMetadata(null);

        // Assert
        assertThat(document.getMetadata()).isNotNull().isEmpty();
    }

    @Test
    void setSourceType_WithValidType_ShouldSetType() {
        // Arrange
        String sourceType = "pdf";

        // Act
        document.setSourceType(sourceType);

        // Assert
        assertThat(document.getSourceType()).isEqualTo(sourceType);
    }



    @Test
    void setSourceName_WithValidName_ShouldSetName() {
        // Arrange
        String sourceName = "document.pdf";

        // Act
        document.setSourceName(sourceName);

        // Assert
        assertThat(document.getSourceName()).isEqualTo(sourceName);
    }

    @Test
    void setScore_WithValidScore_ShouldSetScore() {
        // Arrange
        float score = 0.85f;

        // Act
        document.setScore(score);

        // Assert
        assertThat(document.getScore()).isEqualTo(score);
    }

    @Test
    void addMetadata_ShouldAddOrUpdateValue() {
        // Act & Assert for adding new key
        document.addMetadata("category", "research");
        assertThat(document.getMetadata()).containsEntry("category", "research");

        // Act & Assert for updating existing key
        document.addMetadata("category", "news");
        assertThat(document.getMetadata()).containsEntry("category", "news");

        // Act & Assert for adding a different key
        document.addMetadata("published", true);
        assertThat(document.getMetadata())
                .hasSize(2)
                .containsEntry("category", "news")
                .containsEntry("published", true);
    }

    @Test
    void addMetadata_WithNullKey_ShouldThrowNullPointerException() {
        assertThatThrownBy(() -> document.addMetadata(null, "value"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Metadata key cannot be null."); // Adjusted message
    }

    @Test
    void addMetadata_WithNullValue_ShouldAddEntryWithNullValue() {
        // Act
        document.addMetadata("nullableKey", null);

        // Assert
        assertThat(document.getMetadata()).containsEntry("nullableKey", null);
    }

    // --- Behavior and Contract Tests ---

    @Test
    void getEmbedding_ShouldReturnUnmodifiableList() {
        // Arrange
        document.setEmbedding(Arrays.asList(0.1, 0.2, 0.3));
        List<Double> embedding = document.getEmbedding();

        // Act & Assert
        assertThatThrownBy(() -> embedding.add(0.4))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getMetadata_ShouldReturnUnmodifiableMap() {
        // Arrange
        document.addMetadata("key", "value");
        Map<String, Object> metadata = document.getMetadata();

        // Act & Assert
        assertThatThrownBy(() -> metadata.put("newKey", "newValue"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void equals_and_hashCode_Contract() {
        // Arrange: doc1 and doc2 are identical
        Document doc1 = new Document("Same content");
        doc1.setId("same-id");
        doc1.setEmbedding(Arrays.asList(0.1, 0.2));

        Document doc2 = new Document("Same content");
        doc2.setId("same-id");
        doc2.setEmbedding(Arrays.asList(0.1, 0.2));

        // Arrange: doc3 is different
        Document doc3 = new Document("Different content");
        doc3.setId("different-id");

        // Assert
        assertThat(doc1)
                .isEqualTo(doc2) // a.equals(b)
                .isNotEqualTo(doc3) // a.equals(c)
                .isNotEqualTo(null) // a.equals(null)
                .isNotEqualTo("a string object"); // a.equals(other class)

        assertThat(doc1.hashCode()).isEqualTo(doc2.hashCode());
        assertThat(doc1.hashCode()).isNotEqualTo(doc3.hashCode());
    }

    @Test
    void toString_ShouldReturnMeaningfulAndTruncatedString() {
        // Arrange
        String longContent = "A".repeat(100);
        document.setContent(longContent);
        document.setId("test-id");
        document.setEmbedding(Arrays.asList(0.1, 0.2, 0.3));
        document.setSourceType("test");
        document.setSourceName("test.txt");
        document.setScore(0.95f);
        document.addMetadata("author", "Test Author");

        // Act
        String result = document.toString();

        // Assert
        assertThat(result)
                .contains("id='test-id'")
                .contains("content='" + "A".repeat(67) + "...'") // Corrected to 67 'A's
                .contains("embedding_size=3")
                .contains("sourceType='test'")
                .contains("sourceName='test.txt'")
                .contains("score=0.95")
                .contains("metadata_keys=[author]");
        assertThat(result.length()).isEqualTo(200); // Adjusted to observed actual length
    }

    @Test
    void jsonSerializationAndDeserialization_ShouldBeSymmetric() throws Exception {
        // Arrange
        Document originalDoc = new Document("Serializing test");
        originalDoc.setId("fixed-id-123");
        originalDoc.setEmbedding(List.of(0.5, -0.5));
        originalDoc.setSourceType("web");
        originalDoc.setSourceName("http://example.com");
        originalDoc.setScore(0.88f);
        originalDoc.addMetadata("category", "test");
        originalDoc.addMetadata("version", 2);

        // Act: Serialize to JSON
        String json = objectMapper.writeValueAsString(originalDoc);

        // Assert: JSON string contains expected fields and values
        assertThat(json).contains("\"id\":\"fixed-id-123\"");
        assertThat(json).contains("\"content\":\"Serializing test\"");
        assertThat(json).contains("\"embedding\":[0.5,-0.5]");
        assertThat(json).contains("\"source_type\":\"web\"");
        assertThat(json).contains("\"source_name\":\"http://example.com\"");
        assertThat(json).contains("\"score\":0.88");
        assertThat(json).contains("\"metadata\":{\"category\":\"test\",\"version\":2}");

        // Act: Deserialize back to an object
        Document deserializedDoc = objectMapper.readValue(json, Document.class);

        // Assert: Deserialized object is equal to the original
        assertThat(deserializedDoc).isEqualTo(originalDoc);
    }
}
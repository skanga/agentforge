package com.skanga.rag.embeddings;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class VoyageEmbeddingProviderTests {

    private final String testApiKey = "vy-testapikey"; // Voyage API Key
    private final String testModel = "voyage-2"; // Example model

    @Test
    void constructor_validArgs_initializes() {
        VoyageEmbeddingProvider provider = new VoyageEmbeddingProvider(testApiKey, testModel);
        assertNotNull(provider);
        // Further checks on internal fields would require them to be accessible or tested via behavior.
    }

    @Test
    void constructor_nullApiKey_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new VoyageEmbeddingProvider(null, testModel));
    }

    @Test
    void constructor_nullModelName_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new VoyageEmbeddingProvider(testApiKey, null));
    }

    @Test
    void embedText_emptyText_throwsEmbeddingException() {
        VoyageEmbeddingProvider provider = new VoyageEmbeddingProvider(testApiKey, testModel);
        Exception exception = assertThrows(EmbeddingException.class, () -> {
            provider.embedText("");
        });
        assertTrue(exception.getMessage().contains("Text to embed cannot be empty"));

        Exception exception2 = assertThrows(EmbeddingException.class, () -> {
            provider.embedText("   ");
        });
        assertTrue(exception2.getMessage().contains("Text to embed cannot be empty"));
    }

    @Test
    void embedText_nullText_throwsNullPointerException() {
        VoyageEmbeddingProvider provider = new VoyageEmbeddingProvider(testApiKey, testModel);
        assertThrows(NullPointerException.class, () -> provider.embedText(null));
    }

    @Test
    void embedText_skeletonImplementation_returnsEmptyListOrThrows() {
        VoyageEmbeddingProvider provider = new VoyageEmbeddingProvider(testApiKey, testModel);
        String sampleText = "This is a test for the skeleton Voyage embedder.";

        // Current skeleton returns empty list and prints to System.err
        // If it were to throw UnsupportedOperationException, that would be tested here.
        List<Double> result = provider.embedText(sampleText);
        assertNotNull(result);
        assertTrue(result.isEmpty(), "Skeleton implementation should return an empty list.");

        // To test for UnsupportedOperationException if that was the skeleton's behavior:
        // assertThrows(UnsupportedOperationException.class, () -> {
        //     provider.embedText(sampleText);
        // });
    }

    // When VoyageEmbeddingProvider is fully implemented, add tests similar to OpenAI/Ollama:
    // - testEmbedText_successfulApiCall_returnsEmbeddingList()
    // - testEmbedText_apiReturnsError_throwsEmbeddingException()
    // - testEmbedText_requestPayloadIncludesCorrectFields() (if specific fields like input_type exist)
}

package com.skanga.rag;

import com.skanga.chat.enums.MessageRole;
import com.skanga.chat.history.ChatHistory;
import com.skanga.chat.history.InMemoryChatHistory;
import com.skanga.chat.messages.Message;
import com.skanga.core.AgentObserver;
import com.skanga.core.exceptions.AgentException;
import com.skanga.observability.events.InstructionsChanged;
import com.skanga.observability.events.VectorStoreResult;
import com.skanga.providers.AIProvider;
import com.skanga.rag.embeddings.EmbeddingProvider;
import com.skanga.rag.postprocessing.PostProcessor;
import com.skanga.rag.vectorstore.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RAG Agent Tests")
class RAGTests {

    @Mock
    private EmbeddingProvider embeddingProvider;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private AIProvider aiProvider;
    @Mock
    private PostProcessor postProcessor1;
    @Mock
    private PostProcessor postProcessor2;
    @Mock
    private AgentObserver observer;

    private RAG rag;
    private ChatHistory chatHistory;

    @BeforeEach
    void setUp() {
        chatHistory = new InMemoryChatHistory(10);
        rag = (RAG) new RAG(embeddingProvider, vectorStore)
                .withProvider(aiProvider)
                .withChatHistory(chatHistory);
        rag.addObserver(observer, "*");
    }

    private Document createTestDocument(String id, String content) {
        Document doc = new Document(content);
        doc.setId(id);
        doc.setEmbedding(Arrays.asList(0.1, 0.2, 0.3));
        doc.setSourceName("test.txt");
        doc.setSourceType("test");
        return doc;
    }

    @Nested
    @DisplayName("Constructor and Configuration")
    class ConstructorAndConfigTests {

        @Test
        void constructor_withValidProviders_shouldCreateInstance() {
            assertThat(rag).isNotNull();
            assertThat(rag.getEmbeddingProvider()).isSameAs(embeddingProvider);
            assertThat(rag.getVectorStore()).isSameAs(vectorStore);
        }

        @Test
        void constructor_withNullEmbeddingProvider_shouldThrowException() {
            assertThrows(NullPointerException.class, () -> new RAG(null, vectorStore));
        }

        @Test
        void constructor_withNullVectorStore_shouldThrowException() {
            assertThrows(NullPointerException.class, () -> new RAG(embeddingProvider, null));
        }

        @Test
        void setTopK_withValidValue_shouldSetTopK() {
            rag.setTopK(10);
            // Assuming topK field is accessible for verification or reflected in behavior
            // We verify behavior in retrieveDocuments tests
            assertDoesNotThrow(() -> rag.setTopK(10));
        }

        @Test
        void setTopK_withInvalidValue_shouldThrowException() {
            assertThrows(IllegalArgumentException.class, () -> rag.setTopK(0));
            assertThrows(IllegalArgumentException.class, () -> rag.setTopK(-1));
        }

        @Test
        void addPostProcessor_shouldAddProcessorToList() {
            rag.addPostProcessor(postProcessor1);
            assertThat(rag.getPostProcessors()).contains(postProcessor1);
        }
    }

    @Nested
    @DisplayName("addDocuments Method")
    class AddDocumentsTests {

        @Test
        void addDocuments_withValidDocs_shouldEmbedAndStoreAndNotify() {
            // Arrange
            List<Document> documents = Arrays.asList(new Document("Content 1"), new Document("Content 2"));
            when(embeddingProvider.embedDocuments(documents)).thenReturn(documents);

            // Act
            rag.addDocuments(documents);

            // Assert
            InOrder inOrder = inOrder(observer, embeddingProvider, vectorStore);
            inOrder.verify(observer).update(eq("rag-adddocuments-start"), any());
            inOrder.verify(observer).update(eq("rag-embedding-documents-start"), any());
            inOrder.verify(embeddingProvider).embedDocuments(documents);
            inOrder.verify(observer).update(eq("rag-embedding-documents-end"), any());
            inOrder.verify(observer).update(eq("rag-vectorstore-adding-documents"), any());
            inOrder.verify(vectorStore).addDocuments(documents);
            inOrder.verify(observer).update(eq("rag-vectorstore-added-documents"), any());
            inOrder.verify(observer).update(eq("rag-adddocuments-stop"), any());
        }

        @Test
        void addDocuments_withNullOrEmptyList_shouldDoNothing() {
            assertDoesNotThrow(() -> rag.addDocuments(null));
            assertDoesNotThrow(() -> rag.addDocuments(Collections.emptyList()));
            verifyNoInteractions(embeddingProvider, vectorStore, observer);
        }

        @Test
        void addDocuments_whenEmbeddingFails_shouldThrowAgentException() {
            // Arrange
            List<Document> documents = List.of(new Document("Test content"));
            when(embeddingProvider.embedDocuments(documents)).thenThrow(new RuntimeException("Embedding failed"));

            // Act & Assert
            AgentException exception = assertThrows(AgentException.class, () -> rag.addDocuments(documents));
            assertThat(exception.getMessage()).contains("Failed to embed documents");
            assertThat(exception.getCause()).isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("retrieveDocuments Method")
    class RetrieveDocumentsTests {

        private final Message question = new Message(MessageRole.USER, "What is RAG?");
        private final List<Double> queryEmbedding = List.of(0.5, 0.5);

        @BeforeEach
        void setupRetrieval() {
            when(embeddingProvider.embedText((String) question.getContent())).thenReturn(queryEmbedding);
        }

        @Test
        void retrieveDocuments_withoutPostProcessors_shouldReturnUniqueDocuments() {
            // Arrange
            List<Document> documents = List.of(createTestDocument("doc1", "RAG is cool."), createTestDocument("doc2", "RAG is a technique."));
            when(vectorStore.similaritySearch(queryEmbedding, 5)).thenReturn(documents); // Using default topK

            // Act
            List<Document> result = rag.retrieveDocuments(question);

            // Assert
            assertThat(result).hasSize(2).isEqualTo(documents);
            verify(embeddingProvider).embedText((String) question.getContent());
            verify(vectorStore).similaritySearch(queryEmbedding, 5);
            verify(observer, never()).update(eq("rag-postprocessing-start"), any());
        }

        @Test
        void retrieveDocuments_withSinglePostProcessorAndDuplicates_shouldDeduplicateAndProcess() {
            // Arrange
            Document doc1 = createTestDocument("doc1", "RAG is Retrieval Augmented Generation.");
            Document doc2 = createTestDocument("doc2", "It combines retrieval with generation.");
            Document doc3 = createTestDocument("doc3", "RAG is Retrieval Augmented Generation."); // Duplicate content
            List<Document> rawRetrievedDocs = List.of(doc1, doc2, doc3);
            List<Document> uniqueDocs = List.of(doc1, doc2);
            List<Document> processedDocs = new ArrayList<>(uniqueDocs);
            Collections.reverse(processedDocs); // Simulate post-processor re-ranking

            rag.addPostProcessor(postProcessor1).setTopK(3);
            when(vectorStore.similaritySearch(queryEmbedding, 3)).thenReturn(rawRetrievedDocs);
            when(postProcessor1.process(eq(question), eq(uniqueDocs))).thenReturn(processedDocs);

            // Act
            List<Document> finalDocs = rag.retrieveDocuments(question);

            // Assert
            assertThat(finalDocs).hasSize(2);
            assertThat(finalDocs.get(0).getContent()).isEqualTo(doc2.getContent()); // Re-ranked by post-processor
            assertThat(finalDocs.get(1).getContent()).isEqualTo(doc1.getContent());

            InOrder inOrder = inOrder(observer, vectorStore, postProcessor1);
            inOrder.verify(observer).update(eq("rag-retrieval-start"), any());
            inOrder.verify(observer).update(eq("rag-vectorstore-embedding-query"), any());
            inOrder.verify(observer).update(eq("rag-vectorstore-searching"), any());
            inOrder.verify(vectorStore).similaritySearch(queryEmbedding, 3);
            inOrder.verify(observer).update(eq("rag-vectorstore-result"), any(VectorStoreResult.class));
            inOrder.verify(observer).update(eq("rag-postprocessing-start"), any());
            inOrder.verify(postProcessor1).process(eq(question), eq(uniqueDocs));
            inOrder.verify(observer).update(eq("rag-postprocessing-end"), any());
            inOrder.verify(observer).update(eq("rag-retrieval-stop"), any());
        }

        @Test
        void retrieveDocuments_withMultiplePostProcessors_shouldApplyInOrder() {
            // Arrange
            Document doc1 = createTestDocument("doc1", "Content 1");
            Document doc2 = createTestDocument("doc2", "Content 2");
            List<Document> initialDocs = List.of(doc1, doc2);
            List<Document> docsAfterP1 = List.of(doc1); // P1 filters
            List<Document> docsAfterP2 = new ArrayList<>(docsAfterP1);
            Collections.reverse(docsAfterP2); // P2 re-ranks

            rag.addPostProcessor(postProcessor1).addPostProcessor(postProcessor2).setTopK(2);
            when(vectorStore.similaritySearch(queryEmbedding, 2)).thenReturn(initialDocs);
            when(postProcessor1.process(question, initialDocs)).thenReturn(docsAfterP1);
            when(postProcessor2.process(question, docsAfterP1)).thenReturn(docsAfterP2);

            // Act
            List<Document> result = rag.retrieveDocuments(question);

            // Assert
            assertThat(result).isEqualTo(docsAfterP2);
            InOrder inOrder = inOrder(postProcessor1, postProcessor2);
            inOrder.verify(postProcessor1).process(question, initialDocs);
            inOrder.verify(postProcessor2).process(question, docsAfterP1);
        }

        @Test
        void retrieveDocuments_whenVectorStoreReturnsEmpty_shouldReturnEmptyList() {
            // Arrange
            when(vectorStore.similaritySearch(anyList(), anyInt())).thenReturn(Collections.emptyList());

            // Act
            List<Document> result = rag.retrieveDocuments(question);

            // Assert
            assertThat(result).isNotNull().isEmpty();
            verify(postProcessor1, never()).process(any(), any());
        }

        @Test
        void retrieveDocuments_whenVectorStoreFails_shouldThrowAgentException() {
            // Arrange
            when(vectorStore.similaritySearch(anyList(), anyInt())).thenThrow(new RuntimeException("DB connection failed"));

            // Act & Assert
            AgentException e = assertThrows(AgentException.class, () -> rag.retrieveDocuments(question));
            assertThat(e.getMessage()).contains("Failed to retrieve documents from vector store");
            assertThat(e.getCause()).isInstanceOf(RuntimeException.class);
        }

        @Test
        void retrieveDocuments_withInvalidQuestion_shouldThrowAgentException() {
            assertThrows(NullPointerException.class, () -> rag.retrieveDocuments(null));

            Message emptyQuestion = new Message(MessageRole.USER, "");
            AgentException e1 = assertThrows(AgentException.class, () -> rag.retrieveDocuments(emptyQuestion));
            assertThat(e1.getMessage()).contains("Question content must be a non-empty string");

            Message nullContentQuestion = new Message(MessageRole.USER, null);
            AgentException e2 = assertThrows(AgentException.class, () -> rag.retrieveDocuments(nullContentQuestion));
            assertThat(e2.getMessage()).contains("Question content must be a non-empty string");
        }
    }

    @Nested
    @DisplayName("Generation (answer/streamAnswer) Methods")
    class GenerationTests {

        private final Message question = new Message(MessageRole.USER, "What is the capital of France?");
        private final List<Double> queryEmbedding = List.of(1.0, 2.0);

        @Test
        void answer_withFullRagFlow_shouldRetrieveAugmentAndGenerate() {
            // Arrange
            Message expectedResponse = new Message(MessageRole.ASSISTANT, "Based on context, it is Paris.");
            Document contextDoc = createTestDocument("doc1", "France's capital is Paris.");
            List<Document> retrievedDocs = List.of(contextDoc);

            when(embeddingProvider.embedText((String) question.getContent())).thenReturn(queryEmbedding);
            when(vectorStore.similaritySearch(eq(queryEmbedding), anyInt())).thenReturn(retrievedDocs);
            when(aiProvider.chatAsync(anyList(), anyString(), anyList()))
                    .thenReturn(CompletableFuture.completedFuture(expectedResponse));

            // Act
            Message actualResponse = rag.answer(question);

            // Assert
            assertThat(actualResponse).isEqualTo(expectedResponse);

            // Verify that the augmented instructions were passed to the AI provider
            verify(aiProvider).chatAsync(anyList(), argThat(instructions ->
                    instructions.contains("<EXTRA-CONTEXT>") &&
                            instructions.contains("France's capital is Paris.")
            ), anyList());

            InOrder inOrder = inOrder(observer, aiProvider);
            inOrder.verify(observer).update(eq("rag-answer-start"), any());
            inOrder.verify(observer).update(eq("rag-retrieval-start"), any());
            inOrder.verify(observer).update(eq("rag-retrieval-stop"), any());
            inOrder.verify(observer).update(eq("instructions-changed"), any(InstructionsChanged.class));
            inOrder.verify(aiProvider).chatAsync(anyList(), anyString(), anyList());
            inOrder.verify(observer).update(eq("rag-answer-stop"), any());
        }

        @Test
        void streamAnswer_withValidQuestion_shouldReturnStreamingResponse() {
            // Arrange
            Stream<String> aiStream = Stream.of("Paris", " is the", " capital.");
            when(embeddingProvider.embedText((String) question.getContent())).thenReturn(queryEmbedding);
            when(vectorStore.similaritySearch(anyList(), anyInt())).thenReturn(Collections.emptyList()); // No docs found is a valid case
            when(aiProvider.stream(anyList(), anyString(), anyList())).thenReturn(aiStream);

            // Act
            Stream<String> resultStream = rag.streamAnswer(question);
            String result = resultStream.collect(Collectors.joining());

            // Assert
            assertThat(result).isEqualTo("Paris is the capital.");
            verify(embeddingProvider).embedText((String) question.getContent());
            verify(aiProvider).stream(anyList(), anyString(), anyList());

            InOrder inOrder = inOrder(observer, aiProvider);
            inOrder.verify(observer).update(eq("rag-answer-start"), any());
            inOrder.verify(observer).update(eq("rag-retrieval-start"), any());
            inOrder.verify(observer).update(eq("rag-retrieval-stop"), any());
            inOrder.verify(aiProvider).stream(anyList(), anyString(), anyList());
            // Note: stop event would be fired after stream consumption in a real scenario
        }

        @Test
        void answer_withNullQuestion_shouldThrowException() {
            assertThrows(NullPointerException.class, () -> rag.answer(null));
        }
    }

    @Nested
    @DisplayName("Context Management")
    class ContextManagementTests {

        @Test
        void withDocumentsContext_shouldAddAndRemoveContextFromInstructions() {
            // Arrange
            String initialInstructions = "You are an assistant.";
            rag.withInstructions(initialInstructions);
            Document doc1 = createTestDocument("d1", "Context one.");
            doc1.setSourceName("SourceA");
            List<Document> docs = List.of(doc1);

            // Act 1: Add context
            rag.withDocumentsContext(docs);
            String instructionsWithContext = rag.getInstructions();

            // Assert 1
            assertThat(instructionsWithContext)
                    .startsWith(initialInstructions)
                    .contains("<EXTRA-CONTEXT>")
                    .contains("Source: SourceA")
                    .contains("Content: Context one.")
                    .endsWith("</EXTRA-CONTEXT>");
            verify(observer, atLeastOnce()).update(eq("instructions-changed"), any(InstructionsChanged.class));

            // Act 2: Remove context
            rag.withDocumentsContext(Collections.emptyList());
            String instructionsWithoutContext = rag.getInstructions();

            // Assert 2
            assertThat(instructionsWithoutContext.trim()).isEqualTo(initialInstructions);
        }

        @Test
        void withDocumentsContext_whenContextExists_shouldReplaceIt() {
            // Arrange
            String originalInstructions = "Instructions <EXTRA-CONTEXT>Old context</EXTRA-CONTEXT>";
            rag.withInstructions(originalInstructions);
            List<Document> documents = List.of(createTestDocument("doc1", "New content"));

            // Act
            rag.withDocumentsContext(documents);

            // Assert
            String newInstructions = rag.getInstructions();
            assertThat(newInstructions).contains("New content").doesNotContain("Old context");
        }

        @Test
        void withDocumentsContext_withEmptyList_shouldNotAddContextBlock() {
            // Arrange
            String originalInstructions = "You are a helpful assistant.";
            rag.withInstructions(originalInstructions);

            // Act
            rag.withDocumentsContext(Collections.emptyList());

            // Assert
            assertThat(rag.getInstructions()).isEqualTo(originalInstructions);
            assertThat(rag.getInstructions()).doesNotContain("<EXTRA-CONTEXT>");
        }
    }
}
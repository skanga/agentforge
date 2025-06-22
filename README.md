# AgentForge

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Passing-green.svg)](#)

A comprehensive Java framework for building AI agents with Model Context Protocol (MCP) support, Retrieval-Augmented Generation (RAG), and workflow orchestration capabilities.

## 🚀 Features

### Core Capabilities
- **Multi-Provider AI Support**: OpenAI, Anthropic, Gemini, Ollama, Mistral, DeepSeek
- **Model Context Protocol (MCP)**: Full MCP client implementation for tool integration
- **RAG (Retrieval-Augmented Generation)**: Complete document processing and vector search
- **Workflow Orchestration**: Visual workflow builder with conditional logic
- **Tool System**: Extensible tool framework with JSON schema validation
- **Observability**: Built-in monitoring, tracing, and event system

### Advanced Features
- **Streaming Support**: Real-time response streaming for all providers
- **Structured Output**: Type-safe JSON schema validation
- **Multi-modal**: Text, image, and document processing
- **Persistent Chat History**: File-based and in-memory storage
- **Vector Databases**: ChromaDB, Elasticsearch, Pinecone, file-based storage
- **Post-processing**: Document reranking with Cohere and Jina
- **Embeddings**: OpenAI, Ollama, Voyage AI support

## 📋 Table of Contents

- [Quick Start](#-quick-start)
- [Installation](#-installation)
- [Core Concepts](#-core-concepts)
- [Usage Examples](#-usage-examples)
- [Configuration](#-configuration)
- [Architecture](#-architecture)
- [API Reference](#-api-reference)
- [Contributing](#-contributing)
- [License](#-license)

## 🏃 Quick Start

### Basic AI Agent

```java
import com.skanga.core.ConcreteAgent;
import com.skanga.providers.openai.OpenAIProvider;
import com.skanga.chat.history.InMemoryChatHistory;
import com.skanga.core.messages.MessageRequest;
import com.skanga.chat.messages.UserMessage;

// Create an AI agent
Agent agent = ConcreteAgent.make()
    .withProvider(new OpenAIProvider("your-api-key", "gpt-4"))
    .withChatHistory(new InMemoryChatHistory(100))
    .withInstructions("You are a helpful AI assistant.");

// Send a message
Message response = agent.chat(new MessageRequest(
    new UserMessage("What is the capital of France?")
));

System.out.println(response.getContent());
```

### RAG-Enabled Agent

```java
import com.skanga.rag.RAG;
import com.skanga.rag.embeddings.OpenAIEmbeddingProvider;
import com.skanga.rag.vectorstore.MemoryVectorStore;
import com.skanga.rag.dataloader.FileSystemDocumentLoader;

// Create RAG agent
RAG ragAgent = new RAG(
    new OpenAIEmbeddingProvider("your-api-key", "text-embedding-3-small"),
    new MemoryVectorStore(10)
)
.withProvider(new OpenAIProvider("your-api-key", "gpt-4"))
.withInstructions("Answer questions based on the provided context.");

// Load documents
FileSystemDocumentLoader loader = new FileSystemDocumentLoader("./docs");
ragAgent.addDocuments(loader.getDocuments());

// Ask questions with context
Message answer = ragAgent.answer(new UserMessage("What is mentioned about AI safety?"));
```

### MCP Tool Integration

```java
import com.skanga.mcp.McpConnector;
import com.skanga.tools.Tool;

// Connect to MCP server
Map<String, Object> mcpConfig = Map.of(
    "command", "python",
    "args", List.of("mcp_server.py"),
    "env", Map.of("API_KEY", "your-key")
);

McpConnector mcpConnector = new McpConnector(mcpConfig);
List<Tool> mcpTools = mcpConnector.getTools();

// Add tools to agent
Agent toolEnabledAgent = ConcreteAgent.make()
    .withProvider(new OpenAIProvider("your-api-key", "gpt-4"));

for (Tool tool : mcpTools) {
    toolEnabledAgent.addTool(tool);
}
```

## 🛠 Installation

### Maven

```xml
<dependency>
    <groupId>com.skanga</groupId>
    <artifactId>java-mcp-framework</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.skanga:java-mcp-framework:1.0.0'
```

### Requirements

- **Java 17+** (Required for HTTP Client and Records)
- **Maven 3.6+** or **Gradle 7+**

### Optional Dependencies

```xml
<!-- For PDF processing -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.0</version>
</dependency>

<!-- For Elasticsearch vector store -->
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
    <version>8.11.0</version>
</dependency>

<!-- For OpenTelemetry observability -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>1.32.0</version>
</dependency>
```

## 💡 Core Concepts

### Agents

Agents are the primary interface for AI interactions. They orchestrate providers, tools, and chat history.

```java
// Basic agent
Agent agent = ConcreteAgent.make()
    .withProvider(provider)
    .withChatHistory(chatHistory)
    .withInstructions("System prompt");

// Specialized RAG agent
RAG ragAgent = new RAG(embeddingProvider, vectorStore)
    .withProvider(provider);
```

### Providers

Providers abstract different AI service APIs with a unified interface.

```java
// OpenAI
AIProvider openai = new OpenAIProvider("api-key", "gpt-4")
    .systemPrompt("You are helpful");

// Anthropic
AIProvider anthropic = new AnthropicProvider("api-key", "claude-3-sonnet");

// Local Ollama
AIProvider ollama = new OllamaProvider("http://localhost:11434", "llama2");
```

### Tools

Tools extend agent capabilities with external functions.

```java
Tool calculator = new BaseTool("calculator", "Performs calculations")
    .addParameter("expression", PropertyType.STRING, "Math expression", true)
    .setCallable(input -> {
        String expr = (String) input.getArgument("expression");
        return new ToolExecutionResult(evaluateExpression(expr));
    });

agent.addTool(calculator);
```

### Vector Stores

Vector stores provide semantic search capabilities for RAG.

```java
// In-memory store
VectorStore memoryStore = new MemoryVectorStore(10);

// ChromaDB
VectorStore chromaStore = new ChromaVectorStore(
    "http://localhost:8000", "collection-name", 10
);

// Elasticsearch
VectorStore elasticStore = new ElasticsearchVectorStore(
    elasticsearchClient, "index-name", 10
);
```

### Workflows

Workflows orchestrate complex multi-step processes.

```java
Workflow workflow = new Workflow("data-processing")
    .addNode(new DataExtractionNode("extract"))
    .addNode(new ValidationNode("validate"))
    .addNode(new ProcessingNode("process"))
    .addEdge("extract", "validate")
    .addEdge("validate", "process", state -> 
        (Boolean) state.get("validation_passed"))
    .setStartNodeId("extract")
    .setEndNodeId("process");

WorkflowState result = workflow.run(new WorkflowState());
```

## 📚 Usage Examples

### Streaming Responses

```java
Stream<String> stream = agent.stream(new MessageRequest(
    new UserMessage("Write a long story about space exploration")
));

stream.forEach(System.out::print);
```

### Structured Output

```java
public record WeatherReport(String location, int temperature, String condition) {}

WeatherReport weather = agent.structured(
    new MessageRequest(new UserMessage("What's the weather in Paris?")),
    WeatherReport.class,
    3 // max retries
);

System.out.println("Temperature: " + weather.temperature() + "°C");
```

### Multi-modal Input

```java
import com.skanga.chat.attachments.Image;
import com.skanga.chat.enums.AttachmentContentType;

UserMessage imageMessage = new UserMessage("Describe this image");
imageMessage.addAttachment(new Image(
    base64ImageData, 
    AttachmentContentType.BASE64, 
    "image/jpeg"
));

Message response = agent.chat(new MessageRequest(imageMessage));
```

### Document Processing Pipeline

```java
// Load and process documents
FileSystemDocumentLoader loader = new FileSystemDocumentLoader("./documents")
    .withTextSplitter(new DelimiterTextSplitter(1000, "\\n\\n", 50));

List<Document> documents = loader.getDocuments();

// Add post-processing
CohereRerankerPostProcessor reranker = new CohereRerankerPostProcessor(
    "cohere-api-key", "rerank-english-v2.0", 5
);

ragAgent.addPostProcessor(reranker);
ragAgent.addDocuments(documents);
```

### Observability Integration

```java
import com.skanga.observability.LoggingObserver;
import com.skanga.observability.OpenTelemetryAgentMonitor;

// Add logging
agent.addObserver(new LoggingObserver(), "*");

// Add OpenTelemetry tracing
Tracer tracer = GlobalOpenTelemetry.getTracer("my-app");
agent.addObserver(new OpenTelemetryAgentMonitor(tracer), "*");
```

## ⚙️ Configuration

### Environment Variables

```bash
# API Keys
export OPENAI_API_KEY="your-openai-key"
export ANTHROPIC_API_KEY="your-anthropic-key"
export COHERE_API_KEY="your-cohere-key"

# Vector Store Configuration
export CHROMA_HOST="http://localhost:8000"
export ELASTICSEARCH_URL="http://localhost:9200"

# MCP Server Configuration
export MCP_SERVER_COMMAND="python"
export MCP_SERVER_ARGS="server.py,--port,8080"
```

### Configuration Files

```yaml
# application.yml
ai:
  providers:
    openai:
      api-key: ${OPENAI_API_KEY}
      model: "gpt-4"
      max-tokens: 4096
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      model: "claude-3-sonnet"
      
  vector-stores:
    default:
      type: "chroma"
      host: "http://localhost:8000"
      collection: "documents"
      
  embeddings:
    provider: "openai"
    model: "text-embedding-3-small"
    dimensions: 1536
```

### Programmatic Configuration

```java
Map<String, Object> config = Map.of(
    "provider", "openai",
    "model", "gpt-4",
    "temperature", 0.7,
    "max_tokens", 2048
);

AIProvider provider = ProviderFactory.create(config);
```

## 🏗 Architecture

### Core Components

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│     Agents      │    │    Providers    │    │     Tools       │
│                 │◄──►│                 │    │                 │
│ - ConcreteAgent │    │ - OpenAI        │    │ - BaseTool      │
│ - RAG           │    │ - Anthropic     │    │ - MCP Tools     │
│ - BaseAgent     │    │ - Gemini        │    │ - Toolkits      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │   Chat System   │
                    │                 │
                    │ - Messages      │
                    │ - History       │
                    │ - Attachments   │
                    └─────────────────┘
```

### RAG Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Document Loader │───►│  Text Splitter  │───►│   Embeddings    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                                        │
┌─────────────────┐    ┌──────────────────────┐         │
│ Post Processors │◄───│ Vector Store         │◄────────┘
| (refine/rerank) |    | (persist embeddings) |
└─────────────────┘    └──────────────────────┘
         │                       │
         │                       │
         │                       │
         ▼                       ▼
   ┌─────────────────────────────────────────┐
   │              RAG Agent                  │
   │                                         │
   │  ┌─────────────┐  ┌─────────────────┐   │
   │  │ Retrieval   │  │   Generation    │   │
   │  └─────────────┘  └─────────────────┘   │
   │                                         │
   │         ┌─────────────────┐             │
   │         │    Context      │             │
   │         │   Management    │             │
   │         └─────────────────┘             │
   └─────────────────────────────────────────┘
```

### Workflow System

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│     Nodes       │    │     Edges       │    │   Workflow      │
│                 │    │                 │    │                 │
│ - AbstractNode  │    │ - Conditions    │    │ - Execution     │
│ - Custom Nodes  │    │ - Transitions   │    │ - Persistence   │
└─────────────────┘    └─────────────────┘    │ - Observability │
                                              └─────────────────┘
```

### Data Flow

1. **Input Processing**: Messages → Validation → History
2. **Context Augmentation**: RAG retrieval → Document ranking → Context injection
3. **AI Processing**: Provider API call → Tool execution → Response generation
4. **Output Processing**: Structured validation → History storage → Response delivery

## 📖 API Reference

### Core Interfaces

#### Agent Interface
```java
public interface Agent {
    Agent withProvider(AIProvider provider);
    Agent withInstructions(String instructions);
    Agent addTool(Object tool);
    Agent withChatHistory(ChatHistory chatHistory);
    
    Message chat(MessageRequest messages);
    CompletableFuture<Message> chatAsync(MessageRequest messages);
    Stream<String> stream(MessageRequest messages);
    <T> T structured(MessageRequest messages, Class<T> responseClass, int maxRetries);
    
    void addObserver(AgentObserver observer, String event);
    void removeObserver(AgentObserver observer);
}
```

#### AIProvider Interface
```java
public interface AIProvider {
    CompletableFuture<Message> chatAsync(List<Message> messages, String instructions, List<Object> tools);
    Stream<String> stream(List<Message> messages, String instructions, List<Object> tools);
    <T> T structured(List<Message> messages, Class<T> responseClass, Map<String, Object> responseSchema);
    
    AIProvider systemPrompt(String prompt);
    AIProvider setTools(List<Object> tools);
    AIProvider setHttpClient(Object client);
}
```

#### VectorStore Interface
```java
public interface VectorStore {
    void addDocument(Document document) throws VectorStoreException;
    void addDocuments(List<Document> documents) throws VectorStoreException;
    List<Document> similaritySearch(List<Double> queryEmbedding, int k) throws VectorStoreException;
}
```

### Exception Hierarchy

```
BaseAiException
├── AgentException
├── ProviderException
├── ToolException
│   ├── ToolCallableException
│   └── MissingToolParameterException
├── EmbeddingException
├── VectorStoreException
├── PostProcessorException
├── ChatHistoryException
├── WorkflowException
│   ├── WorkflowInterrupt
│   └── WorkflowExportException
└── McpException
```

## 🔍 Monitoring & Observability

### Events

The framework emits detailed events for monitoring:

- `chat-start` / `chat-stop`: Agent conversations
- `inference-start` / `inference-stop`: Provider API calls
- `tool-calling` / `tool-called`: Tool executions
- `vectorstore-searching` / `vectorstore-result`: RAG operations
- `workflow-node-start` / `workflow-node-stop`: Workflow execution
- `structured-output-event`: Structured output processing
- `error`: Error conditions

### Metrics

Key metrics to monitor:

- **Performance**: Response times, token usage, throughput
- **Reliability**: Error rates, retry counts, timeouts
- **Resource Usage**: Memory, CPU, network connections
- **Business**: Conversation counts, tool usage, document retrievals

### Example Monitoring Setup

```java
// Custom observer for metrics
public class MetricsObserver implements AgentObserver {
    private final MeterRegistry meterRegistry;
    
    @Override
    public void update(String eventType, Object eventData) {
        switch (eventType) {
            case "chat-stop" -> {
                ChatStop event = (ChatStop) eventData;
                Timer.Sample.stop(Timer.builder("chat.duration")
                    .register(meterRegistry));
            }
            case "error" -> {
                Counter.builder("errors.total")
                    .tag("type", eventType)
                    .register(meterRegistry)
                    .increment();
            }
        }
    }
}
```

## 🧪 Testing

### Unit Testing

```java
@Test
void testAgentBasicConversation() {
    // Mock provider
    AIProvider mockProvider = Mockito.mock(AIProvider.class);
    when(mockProvider.chatAsync(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(
            new AssistantMessage("Hello!")
        ));
    
    Agent agent = ConcreteAgent.make()
        .withProvider(mockProvider)
        .withChatHistory(new InMemoryChatHistory(10));
    
    Message response = agent.chat(new MessageRequest(
        new UserMessage("Hi")
    ));
    
    assertEquals("Hello!", response.getContent());
}
```

### Integration Testing

```java
@Test
void testRAGEndToEnd() {
    // Use test containers for vector store
    ChromaVectorStore vectorStore = new ChromaVectorStore(
        "http://localhost:8000", "test-collection", 5
    );
    
    RAG ragAgent = new RAG(
        new MockEmbeddingProvider(),
        vectorStore
    );
    
    // Test document ingestion and retrieval
    ragAgent.addDocuments(testDocuments);
    Message answer = ragAgent.answer(new UserMessage("Test question"));
    
    assertNotNull(answer.getContent());
}
```

## 🤝 Contributing

### Development Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/java-mcp-framework.git
   cd java-mcp-framework
   ```

2. **Install dependencies**
   ```bash
   mvn clean install
   ```

3. **Run tests**
   ```bash
   mvn test
   ```

4. **Start development services**
   ```bash
   docker-compose up -d  # ChromaDB, Elasticsearch
   ```

### Code Style

- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use meaningful variable and method names
- Add comprehensive JavaDoc comments
- Maintain test coverage above 80%

### Pull Request Process

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes with tests
4. Run the full test suite: `mvn verify`
5. Commit your changes: `git commit -m 'Add amazing feature'`
6. Push to the branch: `git push origin feature/amazing-feature`
7. Open a Pull Request

### Reporting Issues

Please use the [issue tracker](https://github.com/yourusername/java-mcp-framework/issues) to report bugs or request features. Include:

- Java version
- Framework version
- Minimal reproduction case
- Error logs and stack traces

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [Model Context Protocol](https://modelcontextprotocol.io/) specification
- [OpenAI](https://openai.com/) for API design inspiration
- [LangChain](https://langchain.com/) for architectural patterns
- All contributors and the open-source community

## 📞 Support

- **Documentation**: [https://docs.java-mcp-framework.com](https://docs.java-mcp-framework.com)
- **Discord**: [Join our community](https://discord.gg/java-mcp-framework)
- **Stack Overflow**: Tag questions with `java-mcp-framework`
- **Email**: support@java-mcp-framework.com

---

**Made with ❤️ by the Java MCP Framework team**
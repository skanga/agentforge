# NeuronAI Java: Intelligent Agent Framework

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)](https://github.com/your_org/neuronai-java/actions)
[![Maven Central](https://img.shields.io/maven-central/v/com.neuronai/neuronai-java.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:com.neuronai%20AND%20a:neuronai-java)
[![License](https://img.shields.io/badge/license-MPL--2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)
[![Javadoc](https://img.shields.io/badge/javadoc-available-brightgreen.svg)](https://your_org.github.io/neuronai-java/javadoc/)

## Introduction

NeuronAI Java is a versatile framework for building applications powered by Large Language Models (LLMs). It provides a comprehensive suite of tools and abstractions to streamline the development of AI agents, Retrieval Augmented Generation (RAG) pipelines, complex workflows, and integrations with various LLM providers. This library is a Java port of the original NeuronAI PHP library, aiming to bring its powerful features to the Java ecosystem.

Whether you're looking to create sophisticated chatbots, data-driven Q&A systems, automated task executors, or orchestrated multi-step processes, NeuronAI Java offers the building blocks to accelerate your development.

## Features

*   **Multi-Provider LLM Access:** Easily switch between different LLM providers (OpenAI, Anthropic, Gemini, Ollama, and OpenAI-compatible ones like Deepseek, Mistral) with a unified interface.
*   **Chat & Streaming:** Supports standard chat interactions and streaming responses for real-time applications.
*   **Structured Output:** Enables reliable extraction of structured data (JSON) from LLM responses.
*   **Tool Usage (Function Calling):** Define custom tools that LLMs can intelligently decide to use, allowing agents to interact with external systems and data sources.
*   **Retrieval Augmented Generation (RAG):**
    *   Document loading from various sources (file system, strings).
    *   Text splitting strategies.
    *   Multiple embedding provider integrations.
    *   Vector store support (in-memory, file-based, and clients for ChromaDB, Elasticsearch, Pinecone scaffold).
    *   Document post-processing and reranking (e.g., Cohere Rerank).
*   **Workflow Engine:** Define and execute complex, stateful workflows composed of interconnected nodes and conditional logic. Supports interruption, persistence (in-memory, file-based planned), and resumption.
*   **Model Context Protocol (MCP) Connector:** Integrate external tool servers that adhere to the MCP standard, discovered and executed via stdio.
*   **Observability:**
    *   Built-in logging via SLF4J (`LoggingObserver`).
    *   OpenTelemetry support (`OpenTelemetryAgentMonitor`) for tracing and APM integration, providing insights into agent execution, LLM calls, tool usage, and RAG pipeline performance.

## Prerequisites

*   Java Development Kit (JDK) 17 or later.
*   Apache Maven (for building the project or including as a dependency).

## Installation

### Maven

Add the following dependency to your project's `pom.xml`:

```xml

<dependency>
    <groupId>com.skangacom.skanga</groupId> <!-- Replace with actual groupId if different -->
    <artifactId>neuronai-java</artifactId>
    <version>LATEST_VERSION</version> <!-- Replace with the latest version from Maven Central -->
</dependency>

        <!-- For specific functionalities, you might need additional dependencies: -->
        <!-- PDF Parsing (for PdfTextFileReader) -->
        <!--
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>2.0.30</version>
        </dependency>
        -->
        <!-- Elasticsearch Client (for ElasticsearchVectorStore) -->
        <!--
        <dependency>
            <groupId>co.elastic.clients</groupId>
            <artifactId>elasticsearch-java</artifactId>
            <version>8.11.1</version>
        </dependency>
        <dependency>
            <groupId>jakarta.json</groupId>
            <artifactId>jakarta.json-api</artifactId>
            <version>2.1.2</version>
        </dependency>
        <dependency>
            <groupId>org.glassfish</groupId>
            <artifactId>jakarta.json</artifactId>
            <version>2.0.1</version>
            <classifier>module</classifier>
        </dependency>
        -->
        <!-- Jackson Databind (often pulled transitively, but good to be aware) -->
        <!--
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.15.3</version>
        </dependency>
        -->
        <!-- SLF4J API (for LoggingObserver) and an SLF4J implementation (e.g., Logback, Log4j2) -->
        <!--
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>2.0.9</version>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.4.11</version>
            <scope>runtime</scope>
        </dependency>
        -->
        <!-- OpenTelemetry API (for OpenTelemetryAgentMonitor) -->
        <!--
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-api</artifactId>
            <version>1.35.0</version>
        </dependency>
        -->
        <!-- Pinecone/Cohere clients would also be added here if official Java SDKs are used -->
```

### Gradle (Groovy DSL)

```gradle
implementation 'com.skanga:neuronai-java:LATEST_VERSION' // Replace with actual group/version

// Example for PDFBox
// implementation 'org.apache.pdfbox:pdfbox:2.0.30'

// Example for Elasticsearch
// implementation 'co.elastic.clients:elasticsearch-java:8.11.1'
// implementation 'jakarta.json:jakarta.json-api:2.1.2'
// implementation 'org.glassfish:jakarta.json:2.0.1:module'

// Example for SLF4J and Logback
// implementation 'org.slf4j:slf4j-api:2.0.9'
// runtimeOnly 'ch.qos.logback:logback-classic:1.4.11'

// Example for OpenTelemetry
// implementation 'io.opentelemetry:opentelemetry-api:1.35.0'
```

## Core Concepts & Quick Start

### Agent

The `Agent` is the central component for interacting with LLMs.

```java
import com.skanga.core.Agent;
import com.skanga.chat.messages.Message;
import com.skanga.chat.enums.MessageRole;
import com.skanga.core.messages.MessageRequest;
import com.skanga.providers.openai.OpenAIProvider; // Example provider
// For structured output example:
// import com.fasterxml.jackson.annotation.JsonProperty;
// import java.util.Map;

// public record Person(@JsonProperty("name") String name, @JsonProperty("age") int age) {}

public class AgentQuickStart {
    public static void main(String[] args) {
        // 1. Create an agent (using ConcreteAgent for direct instantiation)
        Agent agent = new com.skanga.core.ConcreteAgent();

        // 2. Configure an LLM Provider
        // Ensure you have OPENAI_API_KEY environment variable set or pass directly
        OpenAIProvider openAI = new OpenAIProvider(System.getenv("OPENAI_API_KEY"), "gpt-3.5-turbo", null);
        agent.withProvider(openAI);

        // 3. Set system instructions
        agent.withInstructions("You are a helpful and concise assistant.");

        // 4. Simple chat
        System.out.println("--- Simple Chat ---");
        Message userMessage = new Message(MessageRole.USER, "Hello, what is the capital of France?");
        MessageRequest chatRequest = new MessageRequest(userMessage);
        Message assistantResponse = agent.chat(chatRequest);
        System.out.println("AI: " + assistantResponse.getContent());

        // 5. Streaming response
        System.out.println("\n--- Streaming Chat ---");
        Message streamUserMessage = new Message(MessageRole.USER, "Tell me a short story about a brave robot.");
        MessageRequest streamRequest = new MessageRequest(streamUserMessage);
        System.out.print("AI Stream: ");
        agent.stream(streamRequest).forEach(System.out::print);
        System.out.println();

        // 6. Structured output (Example - requires Person record and schema)
        // System.out.println("\n--- Structured Output ---");
        // Message structuredQuery = new Message(MessageRole.USER, "Extract person data: John Doe is 30 years old.");
        // // Define expected JSON schema for the Person record
        // Map<String, Object> personSchema = Map.of(
        //     "type", "object",
        //     "properties", Map.of(
        //         "name", Map.of("type", "string", "description", "The person's full name."),
        //         "age", Map.of("type", "integer", "description", "The person's age in years.")
        //     ),
        //     "required", List.of("name", "age")
        // );
        // try {
        //     // The AIProvider's structured method needs to be implemented to use the schema.
        //     // OpenAIProvider.structured() uses JSON mode with schema in prompt.
        //     // Person person = agent.structured(new MessageRequest(structuredQuery), Person.class, 3, personSchema);
        //     // System.out.println("Structured Person: " + person);
        //     System.out.println("Structured Output: (Ensure provider's structured method is fully implemented with schema handling)");
        // } catch (Exception e) {
        //     System.err.println("Structured output error: " + e.getMessage());
        // }
    }
}
```

### Tools (Function Calling)

Define tools for the agent to use.

```java

// ... other imports from AgentQuickStart ...

// In your main or setup:
BaseTool weatherTool=new BaseTool("get_current_weather","Get the current weather in a given location.");
        weatherTool.addParameter("location",PropertyType.STRING,"The city and state, e.g., San Francisco, CA",true);
        weatherTool.addParameter("unit",PropertyType.STRING,"Temperature unit (celsius or fahrenheit)",false)
        .getEnumList().addAll(List.of("celsius","fahrenheit")); // Example of adding enum to last param

        weatherTool.setCallable(input->{
        String location=(String)input.getArgument("location");
        String unit=(String)input.getArgumentOrDefault("unit","celsius");
        // Actual weather lookup logic here...
        System.out.println("Tool: get_current_weather called for "+location+" in "+unit);
        return new ToolExecutionResult(Map.of("temperature","22","unit",unit,"condition","Sunny"));
        });

// Agent setup from AgentQuickStart
// Agent agent = ...;
// agent.addTool(weatherTool);

// Example interaction (LLM might decide to call the tool)
// Message userToolQuery = new Message(MessageRole.USER, "What's the weather like in Boston?");
// Message assistantResponseWithToolCall = agent.chat(new MessageRequest(userToolQuery));
// System.out.println("AI (Tool Call Flow): " + assistantResponseWithToolCall.getContent());
// If assistantResponseWithToolCall.getContent() is a ToolCallMessage, BaseAgent handles it.
```

### RAG (Retrieval Augmented Generation)

Augment LLM responses with retrieved documents.

```java

// ... other imports ...

// RAG agent setup
// RAG ragAgent = new RAG();
// OpenAIEmbeddingProvider embeddingProvider = new OpenAIEmbeddingProvider(System.getenv("OPENAI_API_KEY"), "text-embedding-3-small");
// MemoryVectorStore vectorStore = new MemoryVectorStore();
// ragAgent.setEmbeddingProvider(embeddingProvider);
// ragAgent.setVectorStore(vectorStore);
// ragAgent.withProvider(openAI); // Set an LLM provider for generation

// Add documents
// List<Document> docs = List.of(
//     new Document("The Neuron SDK for Java helps build LLM apps."),
//     new Document("RAG combines retrieval with generative models.")
// );
// ragAgent.addDocuments(docs); // This will embed and then add to vector store

// Ask a question
// Message ragQuestion = new Message(MessageRole.USER, "What is the Neuron SDK for?");
// Message ragResponse = ragAgent.answer(ragQuestion); // answer() handles retrieval and chat
// System.out.println("RAG AI: " + ragResponse.getContent());
```

### Workflow Engine

Orchestrate complex tasks with a graph-based workflow engine.

```java

// ... other imports ...

// Define a simple node
// class GreetingNode extends AbstractNode {
//     public GreetingNode(String id) { super(id); }
//     @Override
//     public WorkflowState run(WorkflowContext context) throws WorkflowInterrupt {
//         String name = (String) context.getCurrentState().get("name");
//         System.out.println("Node " + getId() + ": Hello, " + name + "!");
//         context.getCurrentState().put(getId() + "_output", "Greeted " + name);
//         return context.getCurrentState();
//     }
// }

// Workflow setup
// Workflow workflow = new Workflow();
// GreetingNode nodeA = new GreetingNode("nodeA");
// GreetingNode nodeB = new GreetingNode("nodeB");

// workflow.addNode(nodeA).addNode(nodeB)
//         .setStartNodeId("nodeA")
//         .addEdge("nodeA", "nodeB");

// Run the workflow
// WorkflowState initialState = new WorkflowState(Map.of("name", "WorkflowUser"));
// try {
//     WorkflowState finalState = workflow.run(initialState);
//     System.out.println("Workflow finished. Final state: " + finalState.getAll());
// } catch (WorkflowException | WorkflowInterrupt e) {
//     System.err.println("Workflow error or interrupt: " + e.getMessage());
// }
```

## Modules Overview

*   **`com.skanga.core`**: Core interfaces (`Agent`, `AIProvider`, `AgentObserver`) and base agent logic (`BaseAgent`).
*   **`com.skanga.chat`**: Classes for chat messages (`Message`, `UserMessage`, `AssistantMessage`), attachments, enums, and chat history management.
*   **`com.skanga.providers`**: Implementations for various LLM providers (OpenAI, Anthropic, Gemini, Ollama, etc.) and their message mappers.
*   **`com.skanga.tools`**: Framework for defining and using tools (function calling) with agents, including properties, schemas, and toolkits.
*   **`com.skanga.rag`**: Components for building RAG pipelines: document loaders, text splitters, embedding providers, vector stores, and post-processors.
*   **`com.skanga.mcp`**: Model Context Protocol client and transport for interacting with external MCP tool servers.
*   **`com.skanga.observability`**: Event definitions and observers for logging (SLF4J) and APM tracing (OpenTelemetry).
*   **`com.skanga.workflow`**: Workflow engine components for defining and executing graph-based workflows, including persistence and state management.

## LLM Providers

NeuronAI Java aims to support a variety of LLM providers:

*   **OpenAI:** `OpenAIProvider` (uses `gpt-3.5-turbo`, `gpt-4`, etc.)
*   **Anthropic:** `AnthropicProvider` (uses Claude models)
*   **Google Gemini:** `GeminiProvider` (uses Gemini models like `gemini-pro`)
*   **Ollama:** `OllamaProvider` (connects to a local Ollama instance for various models)
*   **Deepseek:** `DeepseekProvider` (OpenAI-compatible)
*   **Mistral:** `MistralProvider` (OpenAI-compatible via Mistral API)

**Configuration:**
Most providers require an API key and a model name passed to their constructor. Additional parameters (like temperature, max tokens) can often be supplied in a `Map<String, Object>`.

```java
// Example: OpenAI
// OpenAIProvider openAI = new OpenAIProvider("YOUR_OPENAI_KEY", "gpt-4-turbo-preview", Map.of("temperature", 0.7));

// Example: Anthropic
// AnthropicProvider anthropic = new AnthropicProvider("YOUR_ANTHROPIC_KEY", "claude-3-opus-20240229", "2023-06-01", 4096, null);

// Example: Ollama (assumes Ollama running at http://localhost:11434)
// OllamaProvider ollama = new OllamaProvider("http://localhost:11434", "llama2", Map.of("temperature", 0.5));
```

OpenAI-compatible providers like Deepseek, Mistral (using their official APIs), and often Ollama can leverage the `OpenAIProvider`'s structure by extending it and primarily changing the `baseUri`.

## Observability

The library includes observers for logging and tracing:

*   **`LoggingObserver`**: Uses SLF4J to log agent events. You can configure your SLF4J implementation (e.g., Logback, Log4j2) for output.
    ```java
    // Logger logger = LoggerFactory.getLogger(MyApplication.class);
    // agent.addObserver(new LoggingObserver(logger), "*"); // Observe all events
    ```
*   **`OpenTelemetryAgentMonitor`**: Creates OpenTelemetry spans for key agent operations, facilitating APM integration.
    ```java
    // Assuming you have an OpenTelemetry Tracer instance configured
    // Tracer tracer = OpenTelemetry.getGlobalTracer("com.skanga.myApp");
    // agent.addObserver(new OpenTelemetryAgentMonitor(tracer), "*");
    ```
    You need to have the OpenTelemetry SDK set up in your application to collect and export traces. Key traced events include:
    *   `chat-start`/`chat-stop`
    *   `inference-start`/`inference-stop` (LLM calls)
    *   `tool-calling`/`tool-called` (Tool executions)
    *   RAG pipeline events (`rag-vectorstore-searching`, `rag-vectorstore-result`, etc.)
    *   Agent errors.

## MCP Connector

The Model Context Protocol (MCP) connector allows NeuronAI agents to interface with external tool servers that implement the MCP specification. This is useful for leveraging tools written in other languages or running in separate processes.

```java
// import com.skanga.mcp.McpConnector;
// import com.skanga.tools.Tool;
// import java.util.List;
// import java.util.Map;

// Map<String, Object> mcpConfig = Map.of(
//     "command", "/path/to/mcp_server_executable", // Command to start the MCP server
//     "args", List.of("--port", "8080"),       // Arguments for the command
//     "env", Map.of("PYTHONUNBUFFERED", "1")   // Environment variables
// );
// McpConnector mcpConnector = new McpConnector(mcpConfig);
// try {
//     List<Tool> mcpTools = mcpConnector.getTools();
//     mcpTools.forEach(tool -> agent.addTool(tool)); // Add discovered tools to agent
//     // ... use agent with MCP tools ...
// } catch (McpException e) {
//     System.err.println("MCP Error: " + e.getMessage());
// } finally {
//     // mcpConnector.shutdown(); // Shuts down McpClient and transport
// }
```
The `StdioMcpTransport` handles communication over standard input/output with the server process.

## Known Limitations / Differences from PHP Version

*   **PDF Parsing:** `PdfTextFileReader` uses Apache PDFBox 2.x by default. PDFBox 3.x has API changes; users might need to adjust or provide a custom reader if using PDFBox 3.x features directly.
*   **Tool Schema from Class:** The PHP version's `ObjectProperty` could generate a JSON schema from a PHP class definition. This reflective schema generation is currently deferred in the Java `ObjectToolProperty` (schema must be built manually by adding `ToolProperty` instances).
*   **HTTP Client Usage:** The core `OpenAIProvider` (and by extension Deepseek, Mistral) uses Apache HttpClient 5. Newer provider implementations (Anthropic, Gemini, Ollama for chat/embeddings, RAG components like Cohere/Chroma) have started using the JDK 11+ `java.net.http.HttpClient`. This could be standardized in the future.
*   **StdioMcpTransport Timeouts:** The `receive()` method in `StdioMcpTransport` uses blocking `readLine()`. Robust timeout handling for hung processes is an area for future enhancement.
*   **RAG `FileVectorStore`:** The current `FileVectorStore` is simple (JSON-L). For large datasets, its search performance will be limited.
*   **Tool System in PHP:** The PHP version had a more elaborate `Toolkit` that tools were registered with, and the agent held one `Toolkit`. The Java version currently has `Agent` hold a `List<Object>` for tools, and `Toolkit` is an interface for grouping. `BaseAgent.bootstrapTools()` is a placeholder.
*   **Dynamic Tool Creation in `McpConnector`:** The parsing of `inputSchema` in `McpConnector` to `ToolProperty` objects is a direct port of the structural mapping. Advanced JSON schema features might require more sophisticated parsing.

## Contributing

Contributions are welcome! Please refer to `CONTRIBUTING.md` for guidelines (Note: `CONTRIBUTING.md` would need to be created).
Key areas for contribution include:
*   Implementing more LLM providers and vector stores.
*   Adding more diverse `DocumentLoader` and `FileReader` implementations.
*   Enhancing the Workflow Engine with more node types and features.
*   Improving test coverage.
*   Refining existing components for performance and features.

## License

This project is licensed under the **Mozilla Public License Version 2.0**.
See the [LICENSE](LICENSE) file for details (Note: `LICENSE` file would need to be added with MPL-2.0 text).

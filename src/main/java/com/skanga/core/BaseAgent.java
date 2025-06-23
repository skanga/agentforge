package com.skanga.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.chat.enums.MessageRole;
import com.skanga.chat.history.ChatHistory;
import com.skanga.chat.messages.Message;
import com.skanga.core.exceptions.AgentException;
import com.skanga.core.messages.MessageRequest;
import com.skanga.core.messages.ToolCallMessage;
import com.skanga.core.messages.ToolCallResultMessage;
import com.skanga.providers.AIProvider;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An abstract base class implementing the {@link Agent} interface.
 * It provides common functionalities such as managing providers, instructions, tools,
 * chat history, and observers. It also includes foundational logic for chat, streaming,
 * and structured output interactions, ported from PHP traits.
 *
 * Concrete agent implementations should extend this class.
 *
 * Notable differences or areas for future refinement from PHP version:
 * - Tool Execution: The `executeTools` and `findTool` methods are placeholders and require
 *   a robust mechanism for tool registration, discovery, and invocation (e.g., using reflection
 *   or a structured `com.skanga.tools.Tool` interface). The current implementation
 *   is a direct port of a simplified PHP logic.
 * - Message Handling: The `com.skanga.chat.messages.Message` class is now used, which is different
 *   from the previous `com.skanga.core.messages.Message` record. Adjustments in how messages,
 *   especially tool call requests/responses, are packaged and interpreted by AIProvider might be needed.
 * - Error Handling: Exception handling is basic; more specific exceptions could be beneficial.
 * - JSON Processing: Placeholders for `JsonSchema`, `JsonExtractor`, `Deserializer`, `Validator`
 *   in structured output methods need actual implementations (e.g., using Jackson for deserialization).
 */
public abstract class BaseAgent implements Agent, AutoCloseable {

    /** The AI provider used for generating responses. */
    protected AIProvider provider;
    /** System-level instructions for guiding the AI's behavior. */
    protected String instructions;
    /** List of tools available to the agent. Type is Object for now, to be refined. */
    protected List<Object> tools = new CopyOnWriteArrayList<>();
    /** Manages the history of the conversation. */
    protected ChatHistory chatHistory;
    /** List of registered observers for agent events. */
    private final List<WeakReference<AgentObserverMapping>> observers = new ArrayList<>();

    /**
     * Inner class to map an {@link AgentObserver} to a specific event filter string.
     */
    protected static class AgentObserverMapping {
        private final WeakReference<AgentObserver> observerRef;
        private final String event; // Event name or "*" for all

        /**
         * Constructs an AgentObserverMapping.
         * @param observer The observer instance.
         * @param event The event filter string.
         */
        public AgentObserverMapping(AgentObserver observer, String event) {
            this.observerRef = new WeakReference<>(observer);
            this.event = event;
        }

        public AgentObserver getObserver() {
            return observerRef.get(); // May return null if GC'd
        }

        public String getEvent() { return event; }

        public boolean isValid() {
            return observerRef.get() != null;
        }
    }

    // Static factory 'make()' from PHP is omitted as BaseAgent is abstract.
    // Concrete subclasses can provide their own static factory methods.

    @Override
    public Agent withProvider(AIProvider provider) {
        this.provider = Objects.requireNonNull(provider, "AIProvider cannot be null for BaseAgent.withProvider");
        return this;
    }

    @Override
    public AIProvider resolveProvider() {
        if (this.provider == null) {
            throw new AgentException("AIProvider has not been set for this agent.");
        }
        return this.provider;
    }

    @Override
    public Agent withInstructions(String instructions) {
        this.instructions = instructions;
        notifyObservers("instructions-changed", new com.skanga.observability.events.InstructionsChanged(this.instructions, instructions, Map.of("reason", "explicit_set"))); // Event needs to be defined
        return this;
    }

    @Override
    public String getInstructions() {
        return this.instructions;
    }

    @Override
    public Agent addTool(Object tool) {
        // PHP version used a Toolkit object which managed a list of tools.
        // Here, we add directly to a List. A dedicated Toolkit class might be reintroduced.
        this.tools.add(tool);
        notifyObservers("tool-added", tool); // Event needs to be defined
        return this;
    }

    @Override
    public List<Object> getTools() {
        return new ArrayList<>(this.tools); // Return a copy
    }

    @Override
    public Agent withChatHistory(ChatHistory chatHistory) {
        this.chatHistory = chatHistory;
        return this;
    }

    @Override
    public ChatHistory resolveChatHistory() {
        if (this.chatHistory == null) {
            // PHP version could instantiate a default InMemoryChatHistory.
            // Here, we require it to be explicitly set for now.
            throw new AgentException("ChatHistory has not been set for this agent.");
        }
        return this.chatHistory;
    }

    @Override
    public void addObserver(AgentObserver observer, String event) {
        cleanupStaleReferences(); // Clean before adding
        this.observers.add(new WeakReference<>(new AgentObserverMapping(observer, event)));
    }

    @Override
    public void notifyObservers(String eventType, Object data) {
        cleanupStaleReferences(); // Clean before notifying

        Iterator<WeakReference<AgentObserverMapping>> iterator = observers.iterator();
        while (iterator.hasNext()) {
            WeakReference<AgentObserverMapping> ref = iterator.next();
            AgentObserverMapping mapping = ref.get();

            if (mapping == null || mapping.getObserver() == null) {
                iterator.remove(); // Remove stale reference
                continue;
            }

            try {
                if ("*".equals(mapping.getEvent()) || mapping.getEvent().equalsIgnoreCase(eventType)) {
                    mapping.getObserver().update(eventType, data);
                }
            } catch (Exception e) {
                System.err.println("Error notifying observer for event " + eventType + ": " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
    }

    // Helper method to clean up stale references
    private void cleanupStaleReferences() {
        observers.removeIf(ref -> {
            AgentObserverMapping mapping = ref.get();
            return mapping == null || mapping.getObserver() == null;
        });
    }

    public void removeObserver(AgentObserver observer) {
        observers.removeIf(ref -> {
            AgentObserverMapping mapping = ref.get();
            return mapping == null || Objects.equals(mapping.getObserver(), observer);
        });
    }

    public void removeAllObservers() {
        this.observers.clear();
    }

    public void shutdown() {
        removeAllObservers();
        // Add any other cleanup logic here
    }

    @Override
    public void close() {
        shutdown();
    }

    /**
     * Executes the tools requested by the AI model.
     * This is a simplified port. A robust implementation needs a proper tool dispatch mechanism.
     *
     * @param toolCallMessage The message from the AI containing tool call requests.
     * @return A list of results from tool executions.
     */
    protected List<ToolCallResultMessage> executeTools(ToolCallMessage toolCallMessage) {
        notifyObservers("tool-calling", new com.skanga.observability.events.ToolCalling(toolCallMessage)); // Event needs to be defined
        List<ToolCallResultMessage> results = new ArrayList<>();

        if (toolCallMessage == null || toolCallMessage.toolCalls() == null || toolCallMessage.toolCalls().isEmpty()) {
            // No tools to call, or malformed message.
            // notifyObservers("tool-called", results); // Notify with empty results
            return results;
        }

        for (ToolCallMessage.ToolCall toolCall : toolCallMessage.toolCalls()) {
            // Placeholder for actual tool execution.
            // Needs to find the tool by name (toolCall.function().name())
            // and execute it with toolCall.function().arguments().
            // The PHP version iterated over $this->tools which was a Toolkit.
            Object toolInstance = findTool(toolCall.function().name()); // findTool is a placeholder
            String executionResultContent;
            if (toolInstance != null && toolInstance instanceof com.skanga.tools.Tool) { // Check if it's our Tool interface
                com.skanga.tools.Tool executableTool = (com.skanga.tools.Tool) toolInstance;
                try {
                    // Prepare inputs for the tool. Arguments from LLM are a JSON string.
                    // The Tool's executeCallable should handle parsing this if necessary.
                    Map<String, Object> toolInputs = new ObjectMapper().readValue(toolCall.function().arguments(), new TypeReference<Map<String,Object>>() {});
                    executableTool.setInputs(toolInputs);
                    executableTool.setCallId(toolCall.id()); // Pass the call ID to the tool
                    executableTool.executeCallable(); // This updates executableTool.getResult()
                    Object rawResult = executableTool.getResult();
                    // Convert raw result to string for ToolCallResultMessage content.
                    // Complex objects might need specific serialization.
                    executionResultContent = rawResult != null ? new ObjectMapper().writeValueAsString(rawResult) : "null";

                } catch (Exception e) {
                    executionResultContent = "Error executing tool '" + toolCall.function().name() + "': " + e.getMessage();
                     notifyObservers("error", new com.skanga.observability.events.AgentError(e, true, "Tool execution failed: " + toolCall.function().name()));
                }
            } else {
                executionResultContent = "Error: Tool '" + toolCall.function().name() + "' not found or not executable.";
                 notifyObservers("error", new com.skanga.observability.events.AgentError(new AgentException(executionResultContent), false));
            }
            results.add(new ToolCallResultMessage(toolCall.id(), toolCall.function().name(), executionResultContent));
        }
        notifyObservers("tool-called", new com.skanga.observability.events.ToolCalled(toolCallMessage, results, 0L)); // Duration missing
        return results;
    }

    /**
     * Placeholder for finding a tool instance by its name.
     * This needs to be implemented based on how tools are registered with the agent.
     * @param toolName The name of the tool to find.
     * @return The tool object, or null if not found.
     */
    protected Object findTool(String toolName) {
        // PHP: $tool = $this->tools->getTool($toolCall->function->name);
        // Assumes this.tools is a list of com.skanga.tools.Tool instances.
        for (Object tool : this.tools) {
            if (tool instanceof com.skanga.tools.Tool && ((com.skanga.tools.Tool) tool).getName().equals(toolName)) {
                return tool;
            }
        }
        return null;
    }

    /**
     * Resolves the instructions to be used, returning the configured instructions
     * or a default if none are set.
     * @return The instruction string.
     */
    protected String resolveInstructions() {
        return getInstructions() != null ? getInstructions() : "You are a helpful assistant."; // Default instructions
    }

    /**
     * Removes content delimited by specified open and close tags from a text.
     * Useful for cleaning up context blocks in prompts.
     * @param text The text to process.
     * @param openTag The opening tag (e.g., "&lt;EXTRA-CONTEXT&gt;").
     * @param closeTag The closing tag (e.g., "&lt;/EXTRA-CONTEXT&gt;").
     * @return The text with delimited content removed.
     */
    public static String removeDelimitedContent(String text, String openTag, String closeTag) {
        if (text == null || openTag == null || closeTag == null) return text;
        // Regex to match content between openTag and closeTag, including the tags themselves.
        // DOTALL mode `(?s)` allows `.` to match newlines. `Pattern.quote` escapes regex special chars in tags.
        String regex = "(?s)" + Pattern.quote(openTag) + ".*?" + Pattern.quote(closeTag);
        return text.replaceAll(regex, "");
    }

    // --- Chat Handling ---
    @Override
    public Message chat(MessageRequest request) {
        notifyObservers("chat-start", new com.skanga.observability.events.ChatStart(request, Map.of("agent_class", this.getClass().getSimpleName())));
        long startTime = System.currentTimeMillis();
        try {
            Message response = chatAsync(request).join(); // .join() can throw CompletionException
            long durationMs = System.currentTimeMillis() - startTime;
            notifyObservers("chat-stop", new com.skanga.observability.events.ChatStop(request, response, durationMs));
            return response;
        } catch (java.util.concurrent.CompletionException e) {
            long durationMs = System.currentTimeMillis() - startTime;
            Throwable cause = e.getCause(); // First and only declaration of 'cause' in this block
            notifyObservers("error", new com.skanga.observability.events.AgentError(cause, true, "chat() failed"));
            notifyObservers("chat-stop", new com.skanga.observability.events.ChatStop(request, null, durationMs));

            if (cause instanceof AgentException) {
                throw (AgentException) cause;
            } else { // Includes ProviderException (which is RuntimeException) and other Throwables
                throw new AgentException("Error in chat: " + cause.getMessage(), cause);
            }
        } catch (Exception e) { // Catch other exceptions from chatAsync setup or non-CompletionExceptions
            long durationMs = System.currentTimeMillis() - startTime;
            notifyObservers("error", new com.skanga.observability.events.AgentError(e, true, "chat() failed"));
            notifyObservers("chat-stop", new com.skanga.observability.events.ChatStop(request, null, durationMs));
            throw new AgentException("Error in chat: " + e.getMessage(), e);
        }
    }

    // private Throwable unwrapCompletionCause(Throwable t) { // Reverted this helper, unwrapException is used in chatAsync
    //     if (t instanceof java.util.concurrent.CompletionException && t.getCause() != null) {
    //         return t.getCause();
    //     }
    //     return t;
    // }


    @Override
    public CompletableFuture<Message> chatAsync(MessageRequest request) {
        // Event data preparation
        Map<String, Object> agentContext = Map.of("agent_class", this.getClass().getSimpleName(), "provider", provider != null ? provider.getClass().getSimpleName() : "null");

        try {
            // 1. Fill chat history
            // This step was implicit in PHP's $this->chatHistory->addMessages($request->messages)
            // In Java, BaseAgent.fillChatHistory needs to be called.
            fillChatHistory(request);

            // 2. Bootstrap tools (prepare tool definitions for the provider)
            List<Object> toolsForProvider = bootstrapTools();

            // 3. Get messages from history (potentially including newly added ones)
            List<Message> messagesForProvider = resolveChatHistory().getMessages();

            // 4. Notify inference start
            com.skanga.observability.events.InferenceStart inferenceStartEvent = new com.skanga.observability.events.InferenceStart(
                resolveProvider().getClass().getSimpleName(),
                "unknown_model", // Model name needs to be accessible from provider or config
                messagesForProvider,
                toolsForProvider.stream().map(t->Map.<String, Object>of("name", t.toString())).collect(Collectors.toList()), // Simplified tool representation
                Map.of() // Parameters
            );
            notifyObservers("inference-start", inferenceStartEvent);
            long inferenceStartTime = System.currentTimeMillis();

            return resolveProvider().chatAsync(messagesForProvider, resolveInstructions(), toolsForProvider)
                .thenCompose(responseMessage -> { // responseMessage is com.skanga.chat.messages.Message
                    long inferenceDurationMs = System.currentTimeMillis() - inferenceStartTime;
                    notifyObservers("inference-stop", new com.skanga.observability.events.InferenceStop(
                        resolveProvider().getClass().getSimpleName(), "unknown_model", responseMessage, inferenceDurationMs, inferenceStartEvent
                    ));

                    // 5. Handle potential tool calls from responseMessage
                    // The new Message class doesn't have direct tool_calls field like the old record.
                    // Logic to detect tool calls needs to inspect responseMessage.getContent()
                    if (responseMessage.getContent() instanceof ToolCallMessage) {
                        return handleToolCallsAndContinue(responseMessage);
                    } else {
                        addMessageToHistory(responseMessage);
                        return CompletableFuture.completedFuture(responseMessage);
                    }
                })
                .exceptionally(ex -> {
                    Throwable cause = unwrapException(ex);
                    notifyObservers("error", new com.skanga.observability.events.AgentError(cause, true, "chatAsync failed in provider interaction"));

                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    } else {
                        throw new AgentException("Error in async chat provider interaction: " + cause.getMessage(), cause);
                    }
                });
        } catch (Exception e) { // Catch exceptions from setup (e.g. resolveProvider, resolveChatHistory)
            notifyObservers("error", new com.skanga.observability.events.AgentError(e, true, "chatAsync setup failed"));
            return CompletableFuture.failedFuture(new AgentException("Error starting async chat: " + e.getMessage(), e));
        }
    }

    private CompletableFuture<Message> handleToolCallsAndContinue(Message responseMessage) {
        try {
            ToolCallMessage toolCallRequest = (ToolCallMessage) responseMessage.getContent();
            addMessageToHistory(responseMessage);

            List<ToolCallResultMessage> toolResults = executeTools(toolCallRequest);
            for (ToolCallResultMessage resultMsg : toolResults) {
                Message toolResponseMessage = new Message(MessageRole.TOOL, resultMsg);
                addMessageToHistory(toolResponseMessage);
            }

            return chatAsync(new MessageRequest(resolveChatHistory().getMessages()));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(new AgentException("Error handling tool calls: " + e.getMessage(), e));
        }
    }


    // --- Stream Handling ---
    @Override
    public Stream<String> stream(MessageRequest request) {
        // Similar to chatAsync, needs to manage history, tools, and provider call.
        // PHP: HandleStream::stream()
        notifyObservers("stream-start", request); // Define StreamStart event

        fillChatHistory(request);
        List<Object> toolsForProvider = bootstrapTools();
        List<Message> messagesForProvider = resolveChatHistory().getMessages();

        // Accumulate content for history and final message object
        StringBuilder fullContent = new StringBuilder(2048);
        // TODO: Usage for streams is tricky. Some providers send it at the end or not at all for tokens.
        // Placeholder for accumulating usage if possible.
        // final com.skanga.core.Usage[] streamUsage = { com.skanga.core.Usage.empty() };

        // Notify inference start for stream
        com.skanga.observability.events.InferenceStart inferenceStartEvent = new com.skanga.observability.events.InferenceStart(
                resolveProvider().getClass().getSimpleName(),
                "unknown_model",
                messagesForProvider,
                toolsForProvider.stream().map(t->Map.<String, Object>of("name", t.toString())).collect(Collectors.toList()),
                Map.of()
        );

        notifyObservers("inference-start", inferenceStartEvent);
        long streamInfStartTime = System.currentTimeMillis();

        Stream<String> providerStream;
        try {
            providerStream = resolveProvider().stream(
                messagesForProvider,
                resolveInstructions(),
                toolsForProvider
            );
        } catch (Exception e) {
            notifyObservers("error", new com.skanga.observability.events.AgentError(e, true, "stream() setup failed with provider"));
            notifyObservers("stream-stop", request); // Define StreamStop event
            throw new AgentException("Error starting stream with provider: " + e.getMessage(), e);
        }

        // The stream from the provider needs to be wrapped to handle side effects:
        // 1. Accumulate content for history.
        // 2. Notify inference-stop when the stream is exhausted.
        // 3. Save the full message to history.
        // 4. Handle potential errors from the provider's stream.
        return providerStream
            .peek(fullContent::append) // 1. Accumulate content
            // TODO: Add error handling for the stream itself using .handle() or similar if provider stream throws.
            .onClose(() -> { // Executed when the stream is closed, either normally or exceptionally.
                long streamInfDurationMs = System.currentTimeMillis() - streamInfStartTime;
                Message responseMessage = new Message(MessageRole.ASSISTANT, fullContent.toString());
                // TODO: Set usage on responseMessage if streamUsage was updated.
                // responseMessage.setUsage(streamUsage[0]);

                notifyObservers("inference-stop", new com.skanga.observability.events.InferenceStop(
                    resolveProvider().getClass().getSimpleName(), "unknown_model_stream", responseMessage, streamInfDurationMs, inferenceStartEvent
                ));

                // Save full message to history
                notifyObservers("message-saving", new com.skanga.observability.events.MessageSaving(responseMessage, resolveChatHistory()));
                resolveChatHistory().addMessage(responseMessage);
                notifyObservers("message-saved", new com.skanga.observability.events.MessageSaved(responseMessage, resolveChatHistory()));

                notifyObservers("stream-stop", request); // Define StreamStop event
            });
    }

    // --- Structured Handling ---
    @Override
    public <T> T structured(MessageRequest request, Class<T> responseClass, int maxRetries) {
        notifyObservers("structured-start", Map.of("request", request, "responseClass", responseClass.getName(), "maxRetries", maxRetries));
        long overallStartTime = System.currentTimeMillis();

        try {
            fillChatHistory(request); // Add initial request to history
        } catch (Exception e) {
            throw new AgentException("Failed to fill chat history for structured output: " + e.getMessage(), e);
        }

        // Prepare tools for provider (e.g., for function calling if used for structured output)
        List<Object> toolsForProvider = bootstrapTools();

        // Schema generation placeholder - PHP used a JsonSchema class.
        // Object schema = JsonSchema.fromClass(responseClass);
        // For now, schema is passed to provider if provider's `structured` method needs it.
        // OpenAI's JSON mode doesn't take schema directly in API, but uses prompt.
        // Anthropic/Gemini use a tool with the schema.
        // This BaseAgent's `structured` method assumes the AIProvider handles schema details.
        Map<String, Object> schemaForProvider = Map.of(); // Placeholder for actual schema representation if needed by AIProvider directly.

        Message lastLLMResponse = null; // Keep track of the last raw response from LLM
        String lastErrorMessage = null; // For retry loop with error feedback
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                List<Message> messagesForProvider = new ArrayList<>(resolveChatHistory().getMessages());

                if (attempt > 0 && lastErrorMessage != null) {
                    messagesForProvider.add(new Message(MessageRole.USER,
                        "The previous attempt failed. Please correct your response. Error: " + lastErrorMessage));
                }

                String structuredQueryInstructions = resolveInstructions() +
                    "\n\nYou MUST respond with a valid JSON object that conforms to the requested structure. " +
                    "Do not include any other text, explanations, or markdown formatting before or after the JSON object. " +
                    "The JSON should represent an instance of: " + responseClass.getSimpleName();

                CompletableFuture<Message> futureResponse = resolveProvider().chatAsync(
                    messagesForProvider, structuredQueryInstructions, toolsForProvider);
                lastLLMResponse = futureResponse.join();

                if (lastLLMResponse.getContent() instanceof ToolCallMessage) {
                    ToolCallMessage toolCallRequest = (ToolCallMessage) lastLLMResponse.getContent();
                    addMessageToHistory(lastLLMResponse);

                    List<ToolCallResultMessage> toolResults = executeTools(toolCallRequest);
                    for (ToolCallResultMessage trm : toolResults) {
                        Message toolResMsg = new Message(MessageRole.TOOL, trm);
                        addMessageToHistory(toolResMsg);
                    }
                    // After executing tools, we need to continue the loop to get the final structured response from the LLM.
                    // The next iteration will use the updated chat history.

                    lastErrorMessage = "Tool call executed, retrying for final structured response.";
                    if (attempt == maxRetries) {
                        throw new AgentException("Max retries reached, but last attempt resulted in a tool call, not the structured response.");
                    }
                    continue; // Go to next attempt
                }

                // No tool call, proceed to process the response for structured data
                T processedResponse = processResponse(lastLLMResponse, schemaForProvider, responseClass);
                addMessageToHistory(lastLLMResponse);
                notifyObservers("structured-stop", Map.of("result", processedResponse));
                return processedResponse;

            } catch (java.util.concurrent.CompletionException e) {
                Throwable cause = unwrapException(e);
                lastException = (cause instanceof Exception) ? (Exception) cause : new AgentException(cause.getMessage(), cause);
                lastErrorMessage = cause.getMessage();
                notifyObservers("error", new com.skanga.observability.events.AgentError(cause, true, "Structured output provider call failed on attempt " + (attempt+1)));
            } catch (AgentException e) {
                lastException = e;
                lastErrorMessage = e.getMessage();
                notifyObservers("error", new com.skanga.observability.events.AgentError(e, false, "Structured output processing failed on attempt " + (attempt+1)));
            } catch (Exception e) {
                lastException = e;
                lastErrorMessage = e.getMessage();
                notifyObservers("error", new com.skanga.observability.events.AgentError(e, true, "Structured output failed on attempt " + (attempt+1)));
            }

            if (lastLLMResponse != null) {
                addMessageToHistory(lastLLMResponse);
            }

            if (attempt == maxRetries) {
                String errorMsg = "Max retries (" + maxRetries + ") reached for structured output. Last error: " + lastErrorMessage;
                throw new AgentException(errorMsg, lastException);
            }
        } // End retry loop
        // Should not be reached if maxRetries throws, but as a fallback:
        throw new AgentException("Max retries reached for structured output. Last error: " + lastErrorMessage, lastException);
    }

    /**
     * Processes the raw {@link Message} from the AI provider to extract, deserialize, and validate
     * the structured data.
     *
     * @param response      The raw Message from the AI.
     * @param schema        The schema (placeholder for now) to validate against.
     * @param responseClass The target class for deserialization.
     * @param <T>           The type of the structured response.
     * @return An instance of T.
     * @throws AgentException if extraction, deserialization, or validation fails.
     */
    protected <T> T processResponse(Message response, Object schema, Class<T> responseClass) {
        // This method mirrors PHP's HandleStructured::processResponse

        notifyObservers("structured-output-event", com.skanga.observability.events.StructuredOutputEvent.extracting(response, responseClass));

        // 1. Extract JSON string from response content
        // PHP: $json = JsonExtractor::extract($response->content);
        // Here, assume response.getContent() is the JSON string or needs minimal extraction.
        String jsonString;
        if (response.getContent() instanceof String) {
            jsonString = (String) response.getContent();
        } else {
            // If content is not string (e.g. already a Map due to Jackson from provider, or ToolCallMessage)
            // This path needs careful handling. For now, if it's not a string, assume it's an error for JSON extraction.
            notifyObservers("error", new com.skanga.observability.events.AgentError(new AgentException("Structured response content is not a direct string for JSON extraction."), false));
            throw new AgentException("Structured response content from LLM is not a direct string. Actual type: " + (response.getContent() != null ? response.getContent().getClass().getName() : "null"));
        }

        if (jsonString == null || jsonString.trim().isEmpty()) {
            notifyObservers("error", new com.skanga.observability.events.AgentError(new AgentException("Extracted JSON string is null or empty."), false));
            throw new AgentException("Extracted JSON string for structured output is null or empty.");
        }
        // Basic cleanup: Some models might wrap JSON in markdown ```json ... ```
        jsonString = jsonString.trim();
        if (jsonString.startsWith("```json")) {
            jsonString = jsonString.substring(7);
            if (jsonString.endsWith("```")) {
                jsonString = jsonString.substring(0, jsonString.length() - 3);
            }
        } else if (jsonString.startsWith("```")) { // Some models just use ``` without json specifier
             jsonString = jsonString.substring(3);
            if (jsonString.endsWith("```")) {
                jsonString = jsonString.substring(0, jsonString.length() - 3);
            }
        }
        jsonString = jsonString.trim();


        notifyObservers("structured-output-event", com.skanga.observability.events.StructuredOutputEvent.extracted(response, jsonString, responseClass));

        // 2. Deserialize
        // PHP: $object = Deserializer::deserialize($json, $this->getOutputClass());
        notifyObservers("structured-output-event", com.skanga.observability.events.StructuredOutputEvent.deserializing(jsonString, responseClass));
        T deserializedObject;
        try {
            // Using Jackson ObjectMapper, which should be available or passed in.
            // For now, creating a new one. Ideally, reuse from BaseAgent or Provider.
            ObjectMapper localObjectMapper = new ObjectMapper();
            deserializedObject = localObjectMapper.readValue(jsonString, responseClass);
        } catch (JsonProcessingException e) {
            notifyObservers("error", new com.skanga.observability.events.AgentError(e, false, "JSON deserialization failed for structured output."));
            throw new AgentException("Failed to deserialize JSON to " + responseClass.getName() + ". JSON: " + jsonString, e);
        }
        notifyObservers("structured-output-event", com.skanga.observability.events.StructuredOutputEvent.deserialized(jsonString, deserializedObject, responseClass));

        // 3. Validate (Placeholder for now)
        // PHP: $this->validator->validate($object, $schema);
        notifyObservers("structured-output-event", com.skanga.observability.events.StructuredOutputEvent.validating(deserializedObject, schema, responseClass));
        // boolean isValid = true; // Actual validation logic needed here
        // if (!isValid) {
        //     notifyObservers("structured-output-event", com.skanga.observability.events.StructuredOutputEvent.validationFailed(deserializedObject, schema, validationErrors, responseClass));
        //     throw new AgentException("Validation failed for structured output of type " + responseClass.getName());
        // }
        notifyObservers("structured-output-event", com.skanga.observability.events.StructuredOutputEvent.validated(deserializedObject, schema, responseClass));

        return deserializedObject;
    }

    /**
     * Unwraps CompletionException and other wrapper exceptions to get the root cause.
     */
    private Throwable unwrapException(Throwable ex) {
        Throwable cause = ex;
        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    /**
     * Placeholder from PHP's HandleStructured trait.
     * In Java, the target class is passed directly to `structured` and `processResponse`.
     * @throws UnsupportedOperationException always.
     */
    protected String getOutputClass() {
        throw new UnsupportedOperationException("getOutputClass() is not used in this Java implementation. Target class is passed directly.");
    }

    // --- Helper methods (to be implemented or made abstract by concrete agents) ---

    /**
     * Fills the chat history with the current request messages.
     * This version adds messages from {@link MessageRequest} to the agent's {@link ChatHistory}.
     * @param request The current message request.
     */
    protected void fillChatHistory(MessageRequest request) {
        Objects.requireNonNull(request, "MessageRequest cannot be null for fillChatHistory.");
        ChatHistory history = resolveChatHistory(); // Throws if history is null
        if (request.messages() != null) {
            for (Message msg : request.messages()) {
                // Notify before saving each message
                notifyObservers("message-saving", new com.skanga.observability.events.MessageSaving(msg, history));
                history.addMessage(msg);
                notifyObservers("message-saved", new com.skanga.observability.events.MessageSaved(msg, history));
            }
        }
    }

    /**
     * Helper to add a single Message to history, with notifications.
     * @param message The message to add.
     */
    protected void addMessageToHistory(Message message) {
        Objects.requireNonNull(message, "Message cannot be null for addMessageToHistory.");
        ChatHistory history = resolveChatHistory();
        notifyObservers("message-saving", new com.skanga.observability.events.MessageSaving(message, history));
        history.addMessage(message);
        notifyObservers("message-saved", new com.skanga.observability.events.MessageSaved(message, history));
    }


    /**
     * Bootstraps tools into a format suitable for the AI provider.
     * This might involve converting Tool objects into JSON schemas or specific structures.
     * The current implementation returns the raw list of tool objects stored in `this.tools`.
     * Providers are expected to handle this list, potentially by casting to `com.skanga.tools.Tool`
     * and then calling `getJsonSchema()` on each tool.
     * @return A list of tools (currently List&lt;Object&gt;).
     */
    protected List<Object> bootstrapTools() {
        // PHP: $tools = $this->toolSchemaGenerator->generate($this->tools);
        // This Java version currently passes raw tool objects.
        // AIProvider implementations (like OpenAIProvider) will convert these
        // using tool.getJsonSchema() if they are com.skanga.tools.Tool instances.
        if (this.tools == null || this.tools.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(this.tools); // Return a copy
    }
}


package com.skanga.providers.anthropic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.chat.enums.MessageRole;
import com.skanga.chat.messages.AssistantMessage;
import com.skanga.chat.messages.Message;
import com.skanga.providers.AIProvider;
import com.skanga.core.Usage;
import com.skanga.core.exceptions.ProviderException;
import com.skanga.core.messages.ToolCallMessage;
import com.skanga.providers.HttpClientManager;
import com.skanga.providers.ProviderUtils;
import com.skanga.providers.anthropic.dto.*;
import com.skanga.providers.mappers.MessageMapper;
import com.skanga.tools.BaseTool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class AnthropicProvider implements AIProvider {
    private static final String DEFAULT_BASE_URI = "https://api.anthropic.com/v1";
    private static final String DEFAULT_VERSION = "2023-06-01";

    private final String apiKey;
    private final String model;
    private final String baseUri;
    private final String anthropicVersion;
    private final int maxTokens;
    private final Map<String, Object> parameters;
    private final List<Object> tools = new ArrayList<>();
    private final AnthropicMessageMapper messageMapper;
    private static final ObjectMapper jsonObjectMapper = ProviderUtils.getObjectMapper();
    private final AtomicReference<AnthropicStreamMessageUsage> accumulatedStreamUsage = new AtomicReference<>();

    private String systemPrompt;
    private HttpClient httpClient; // Added field

    public AnthropicProvider(String apiKey, String model, Map<String, Object> parameters, String baseUri) {
        this.apiKey = Objects.requireNonNull(apiKey, "API key cannot be null");
        this.model = Objects.requireNonNull(model, "Model cannot be null");
        this.baseUri = baseUri != null ? baseUri : DEFAULT_BASE_URI;
        this.parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
        this.anthropicVersion = DEFAULT_VERSION;
        this.maxTokens = ProviderUtils.getIntValue(this.parameters, "max_tokens", 4096);
        this.messageMapper = new AnthropicMessageMapper();
    }

    public AnthropicProvider(String apiKey, String model, Map<String, Object> parameters) {
        this(apiKey, model, parameters, null);
    }
    public AnthropicProvider(String apiKey, String model) {
        this(apiKey, model, null, null);
    }

    @Override
    public AIProvider systemPrompt(String prompt) {
        this.systemPrompt = prompt;
        return this;
    }

    @Override
    public AIProvider setTools(List<Object> toolObjects) {
        this.tools.clear();
        if (toolObjects != null) {
            for (Object obj : toolObjects) {
                if (obj instanceof com.skanga.tools.Tool) {
                    this.tools.add(obj);
                } else {
                    System.err.println("Warning: Tool object is not an instance of com.skanga.tools.Tool: " +
                        (obj != null ? obj.getClass().getName() : "null"));
                }
            }
        }
        return this;
    }

    @Override
    public MessageMapper messageMapper() {
        return this.messageMapper;
    }

    @Override
    public AIProvider setHttpClient(Object client) {
        if (client instanceof HttpClient) {
            this.httpClient = (HttpClient) client;
        } else if (client == null) {
            this.httpClient = HttpClientManager.getSharedClient(); // Reset to default or handle error
        } else {
            throw new IllegalArgumentException("Client must be an instance of java.net.http.HttpClient");
        }
        return this;
    }

    // Ensure httpClient is initialized if not set via setter (e.g. in constructor)
    private HttpClient getClient() {
        if (this.httpClient == null) {
            this.httpClient = HttpClientManager.getSharedClient();
        }
        return this.httpClient;
    }

    private List<Map<String, Object>> generateToolsPayload(List<com.skanga.tools.Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }

        return tools.stream().map(tool -> {
            Map<String, Object> toolPayload = new HashMap<>();
            toolPayload.put("name", tool.getName());
            toolPayload.put("description", tool.getDescription());
            toolPayload.put("input_schema", tool.getJsonSchema());
            return toolPayload;
        }).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    @Override
    public CompletableFuture<Message> chatAsync(List<Message> messages, String instructions, List<Object> toolObjects) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Map<String, Object>> mappedMessages = this.messageMapper.map(messages);
                Map<String, Object> requestPayload = new HashMap<>();

                requestPayload.put("model", this.model);
                requestPayload.put("max_tokens", this.maxTokens);
                requestPayload.put("messages", mappedMessages);

                // Handle system prompt
                String effectiveSystemPrompt = instructions != null ? instructions : this.systemPrompt;
                if (effectiveSystemPrompt != null && !effectiveSystemPrompt.isEmpty()) {
                    requestPayload.put("system", effectiveSystemPrompt);
                }

                // Handle tools
                List<com.skanga.tools.Tool> currentTools = new ArrayList<>();
                if (toolObjects != null) {
                    for (Object obj : toolObjects) {
                        if (obj instanceof com.skanga.tools.Tool) {
                            currentTools.add((com.skanga.tools.Tool) obj);
                        }
                    }
                }

                List<Map<String, Object>> toolsPayload = generateToolsPayload(currentTools);
                if (toolsPayload != null && !toolsPayload.isEmpty()) {
                    requestPayload.put("tools", toolsPayload);
                }

                // Add other parameters
                requestPayload.putAll(this.parameters);
                requestPayload.remove("stream"); // Ensure not streaming

                String payloadJson = jsonObjectMapper.writeValueAsString(requestPayload);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUri + "/messages"))
                    .header("x-api-key", this.apiKey)
                    .header("anthropic-version", this.anthropicVersion)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(2))
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                    .build();

                HttpResponse<String> response = getClient().send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new ProviderException("Anthropic API request failed", response.statusCode(), response.body());
                }

                Map<String, Object> responseMap = jsonObjectMapper.readValue(response.body(), new TypeReference<>() {});

                String roleStr = ProviderUtils.getStringValue(responseMap, "role", "assistant");
                MessageRole responseRole = roleStr.equalsIgnoreCase("assistant") ? MessageRole.ASSISTANT : MessageRole.MODEL;

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> contentBlocks = (List<Map<String, Object>>) responseMap.get("content");

                StringBuilder textContent = new StringBuilder(1024);
                List<ToolCallMessage.ToolCall> requestedToolCalls = new ArrayList<>();

                if (contentBlocks != null) {
                    for (Map<String, Object> block : contentBlocks) {
                        String type = ProviderUtils.getStringValue(block, "type", "");
                        if ("text".equals(type)) {
                            textContent.append(ProviderUtils.getStringValue(block, "text", ""));
                        } else if ("tool_use".equals(type)) {
                            String toolUseId = ProviderUtils.getStringValue(block, "id", "");
                            String toolName = ProviderUtils.getStringValue(block, "name", "");
                            @SuppressWarnings("unchecked")
                            Map<String, Object> toolInputMap = (Map<String, Object>) block.get("input");
                            String toolInputJson = jsonObjectMapper.writeValueAsString(toolInputMap);
                            requestedToolCalls.add(new ToolCallMessage.ToolCall(
                                toolUseId, "function",
                                new ToolCallMessage.FunctionCall(toolName, toolInputJson)
                            ));
                        }
                    }
                }

                Message resultMessage;
                if (!requestedToolCalls.isEmpty()) {
                    String toolCallMsgId = "tcm-" + System.currentTimeMillis();
                    resultMessage = new AssistantMessage(new ToolCallMessage(toolCallMsgId, requestedToolCalls));
                    if (textContent.length() > 0) {
                        System.err.println("Anthropic response included text alongside tool_use: " + textContent);
                    }
                } else {
                    resultMessage = new AssistantMessage(textContent.toString());
                }

                // Add usage information
                @SuppressWarnings("unchecked")
                Map<String, Integer> usageData = (Map<String, Integer>) responseMap.get("usage");
                if (usageData != null) {
                    Usage usage = new Usage(usageData.get("input_tokens"),
                            usageData.get("output_tokens"),
                            usageData.get("input_tokens") +
                            usageData.get("output_tokens")
                    );
                    resultMessage.setUsage(usage);
                }

                return resultMessage;

            } catch (Exception e) {
                throw new ProviderException("Error during Anthropic API call: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public Message chat(List<Message> messages) {
        try {
            return this.chatAsync(messages, this.systemPrompt, new ArrayList<>(this.tools)).join();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new ProviderException("Error in synchronous Anthropic chat: " + cause.getMessage(), cause);
        }
    }

    @Override
    public Stream<String> stream(List<Message> messages, String instructions, List<Object> toolObjects) {
        try {
            this.accumulatedStreamUsage.set(null);

            List<Map<String, Object>> mappedMessages = this.messageMapper.map(messages);
            Map<String, Object> requestPayload = new HashMap<>();

            requestPayload.put("model", this.model);
            requestPayload.put("max_tokens", this.maxTokens);
            requestPayload.put("messages", mappedMessages);
            requestPayload.put("stream", true);

            String effectiveSystemPrompt = instructions != null ? instructions : this.systemPrompt;
            if (effectiveSystemPrompt != null && !effectiveSystemPrompt.isEmpty()) {
                requestPayload.put("system", effectiveSystemPrompt);
            }

            List<com.skanga.tools.Tool> currentTools = new ArrayList<>();
            if (toolObjects != null) {
                for (Object obj : toolObjects) {
                    if (obj instanceof com.skanga.tools.Tool) {
                        currentTools.add((com.skanga.tools.Tool) obj);
                    }
                }
            }

            List<Map<String, Object>> toolsPayload = generateToolsPayload(currentTools);
            if (toolsPayload != null && !toolsPayload.isEmpty()) {
                requestPayload.put("tools", toolsPayload);
            }

            requestPayload.putAll(this.parameters);
            String payloadJson = jsonObjectMapper.writeValueAsString(requestPayload);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.baseUri + "/messages"))
                .header("x-api-key", this.apiKey)
                .header("anthropic-version", this.anthropicVersion)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                .build();

            HttpResponse<String> response = getClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ProviderException("Anthropic stream request failed", response.statusCode(), response.body());
            }

            return parseAnthropicStream(response.body());

        } catch (Exception e) {
            throw new ProviderException("Error setting up Anthropic stream: " + e.getMessage(), e);
        }
    }

    private Stream<String> parseAnthropicStream(String responseBody) {
        BufferedReader reader = new BufferedReader(new StringReader(responseBody));
        List<String> textChunks = new ArrayList<>();

        try {
            String line;
            String currentEvent = null;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event: ")) {
                    currentEvent = line.substring(7).trim();
                } else if (line.startsWith("data: ")) {
                    String jsonData = line.substring(6).trim();
                    if (jsonData.isEmpty()) continue;

                    try {
                        JsonNode dataNode = jsonObjectMapper.readTree(jsonData);
                        String typeInData = dataNode.path("type").asText();

                        if ("message_start".equals(typeInData)) {
                            AnthropicMessageStartData startData = jsonObjectMapper.treeToValue(dataNode, AnthropicMessageStartData.class);
                            if (startData != null && startData.message() != null) {
                                accumulatedStreamUsage.set(startData.message().usage());
                            }
                        } else if ("content_block_delta".equals(typeInData)) {
                            JsonNode deltaNode = dataNode.path("delta");
                            AnthropicStreamContentBlockDelta delta = jsonObjectMapper.treeToValue(deltaNode, AnthropicStreamContentBlockDelta.class);
                            if ("text_delta".equals(delta.type()) && delta.text() != null) {
                                textChunks.add(delta.text());
                            }
                        } else if ("message_delta".equals(typeInData)) {
                            AnthropicMessageDeltaData deltaData = jsonObjectMapper.treeToValue(dataNode, AnthropicMessageDeltaData.class);
                            if (deltaData.usage() != null) {
                                accumulatedStreamUsage.getAndUpdate(current ->
                                    new AnthropicStreamMessageUsage(
                                        current != null ? current.inputTokens() : 0,
                                        deltaData.usage().outputTokens()
                                    )
                                );
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing Anthropic stream chunk: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            throw new ProviderException("Error reading Anthropic stream", e);
        }

        return textChunks.stream();
    }

    @Override
    public <T> T structured(List<Message> messages, Class<T> responseClass, Map<String, Object> responseSchema) {
        try {
            String dynamicToolName = "extract_" + responseClass.getSimpleName().toLowerCase();
            String toolDescription = "Extract structured data matching the required schema";

            com.skanga.tools.Tool dynamicTool = new BaseTool(dynamicToolName, toolDescription) {
                @Override
                public Map<String, Object> getJsonSchema() {
                    return responseSchema;
                }
            };

            List<com.skanga.tools.Tool> toolsForRequest = Collections.singletonList(dynamicTool);
            List<Map<String, Object>> toolsPayload = generateToolsPayload(toolsForRequest);

            String augmentedSystemInstruction = "You must use the " + dynamicToolName + " tool to provide your response. " +
                "Extract the requested information and call the tool with the structured data.";

            List<Message> allMessages = new ArrayList<>();
            if (augmentedSystemInstruction != null) {
                allMessages.add(new Message(MessageRole.SYSTEM, augmentedSystemInstruction));
            }
            allMessages.addAll(messages);

            List<Map<String, Object>> mappedMessages = this.messageMapper.map(allMessages);
            Map<String, Object> requestPayload = new HashMap<>();

            requestPayload.put("model", this.model);
            requestPayload.put("max_tokens", this.maxTokens);
            requestPayload.put("messages", mappedMessages);

            String finalSystemPrompt = augmentedSystemInstruction;
            if (this.systemPrompt != null && !this.systemPrompt.isEmpty()) {
                finalSystemPrompt = this.systemPrompt + "\n\n" + augmentedSystemInstruction;
            }

            if (finalSystemPrompt != null && !finalSystemPrompt.isEmpty()) {
                requestPayload.put("system", finalSystemPrompt);
            }

            if (toolsPayload != null && !toolsPayload.isEmpty()) {
                requestPayload.put("tools", toolsPayload);
                requestPayload.put("tool_choice", Map.of("type", "tool", "name", dynamicToolName));
            } else {
                throw new ProviderException("Dynamic tool for structured output could not be constructed");
            }

            requestPayload.putAll(this.parameters);
            String payloadJson = jsonObjectMapper.writeValueAsString(requestPayload);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.baseUri + "/messages"))
                .header("x-api-key", this.apiKey)
                .header("anthropic-version", this.anthropicVersion)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                .build();

            HttpResponse<String> response = getClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ProviderException("Anthropic structured output request failed", response.statusCode(), response.body());
            }

            Map<String, Object> responseMap = jsonObjectMapper.readValue(response.body(), new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contentBlocks = (List<Map<String, Object>>) responseMap.get("content");

            if (contentBlocks != null) {
                for (Map<String, Object> block : contentBlocks) {
                    if ("tool_use".equals(block.get("type")) && dynamicToolName.equals(block.get("name"))) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> toolInputMap = (Map<String, Object>) block.get("input");
                        String jsonStringInput = jsonObjectMapper.writeValueAsString(toolInputMap);
                        return jsonObjectMapper.readValue(jsonStringInput, responseClass);
                    }
                }
            }

            throw new ProviderException("Anthropic response did not use the required tool '" + dynamicToolName + "' for structured output. Response: " + response.body());

        } catch (Exception e) {
            throw new ProviderException("Error during Anthropic structured output API call: " + e.getMessage(), e);
        }
    }
}

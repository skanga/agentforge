
// ollama/OllamaProvider.java
package com.skanga.providers.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.chat.enums.MessageRole;
import com.skanga.chat.messages.AssistantMessage;
import com.skanga.chat.messages.Message;
import com.skanga.core.Usage;
import com.skanga.core.messages.ToolCallMessage;
import com.skanga.core.exceptions.ProviderException;
import com.skanga.providers.AIProvider;
import com.skanga.providers.HttpClientManager;
import com.skanga.providers.ProviderUtils;
import com.skanga.providers.mappers.MessageMapper;
import com.skanga.providers.ollama.dto.*;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OllamaProvider implements AIProvider {
    private final String baseUrl;
    private final String modelName;
    final Map<String, Object> parameters;
    private final List<Object> tools = new ArrayList<>();
    private final OllamaMessageMapper messageMapper;
    private static final ObjectMapper jsonObjectMapper = ProviderUtils.getObjectMapper();
    final AtomicInteger streamPromptEvalCount = new AtomicInteger(0);
    final AtomicInteger streamEvalCount = new AtomicInteger(0);

    private String systemPromptText;

    public OllamaProvider(String baseUrl, String modelName, Map<String, Object> parameters) {
        this.baseUrl = baseUrl.endsWith("/api") ? baseUrl.substring(0, baseUrl.length() - 4) :
            (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.modelName = Objects.requireNonNull(modelName, "Model name cannot be null");
        this.parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
        this.messageMapper = new OllamaMessageMapper();
    }

    @Override
    public AIProvider systemPrompt(String prompt) {
        this.systemPromptText = prompt;
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
                    System.err.println("Warning: Tool object is not com.skanga.tools.Tool: " +
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
        // JDK HttpClient doesn't need external setting
        return this;
    }

    private List<Map<String, Object>> generateToolsPayload(List<com.skanga.tools.Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }

        return tools.stream().map(tool -> {
            Map<String, Object> toolMap = new HashMap<>();
            toolMap.put("type", "function");

            Map<String, Object> functionDetails = new HashMap<>();
            functionDetails.put("name", tool.getName());
            functionDetails.put("description", tool.getDescription());
            functionDetails.put("parameters", tool.getJsonSchema());
            toolMap.put("function", functionDetails);
            return toolMap;
        }).collect(Collectors.toList());
    }

    private String determineEffectiveSystemPrompt(String instructionsParam, List<Message> messages) {
        String effectiveSystem = instructionsParam;
        if (effectiveSystem == null) {
            effectiveSystem = this.systemPromptText;
        }

        // Check for system messages in conversation
        for (Message msg : messages) {
            if (msg.getRole() == MessageRole.SYSTEM && msg.getContent() instanceof String) {
                effectiveSystem = (String) msg.getContent();
                break;
            }
        }
        return effectiveSystem;
    }

    @Override
    public CompletableFuture<Message> chatAsync(List<Message> messages, String instructions, List<Object> toolObjects) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String effectiveSystem = determineEffectiveSystemPrompt(instructions, messages);
                List<Message> messagesForMapping = messages.stream()
                    .filter(msg -> msg.getRole() != MessageRole.SYSTEM)
                    .collect(Collectors.toList());
                List<Map<String, Object>> mappedMessages = this.messageMapper.map(messagesForMapping);

                Map<String, Object> requestPayload = new HashMap<>();
                requestPayload.put("model", this.modelName);
                requestPayload.put("messages", mappedMessages);
                if (effectiveSystem != null && !effectiveSystem.isEmpty()) {
                    requestPayload.put("system", effectiveSystem);
                }
                requestPayload.put("stream", false);

                List<com.skanga.tools.Tool> currentTools = new ArrayList<>();
                if (toolObjects != null) {
                    for (Object obj : toolObjects) {
                        if (obj instanceof com.skanga.tools.Tool) {
                            currentTools.add((com.skanga.tools.Tool) obj);
                        }
                    }
                }

                List<Map<String, Object>> ollamaToolsPayload = generateToolsPayload(currentTools);
                if (ollamaToolsPayload != null && !ollamaToolsPayload.isEmpty()) {
                    requestPayload.put("tools", ollamaToolsPayload);
                }

                // Handle Ollama-specific parameters
                if (this.parameters.containsKey("format")) {
                    requestPayload.put("format", this.parameters.get("format"));
                }
                if (this.parameters.containsKey("options")) {
                    requestPayload.put("options", this.parameters.get("options"));
                }
                if (this.parameters.containsKey("keep_alive")) {
                    requestPayload.put("keep_alive", this.parameters.get("keep_alive"));
                }

                String requestUrl = this.baseUrl + "/api/chat";
                String payloadJson = jsonObjectMapper.writeValueAsString(requestPayload);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(2))
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                    .build();

                HttpResponse<String> response = HttpClientManager.getSharedClient().send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new ProviderException("Ollama API request failed", response.statusCode(), response.body());
                }

                OllamaChatResponse ollamaResponse = jsonObjectMapper.readValue(response.body(), OllamaChatResponse.class);

                if (ollamaResponse.message() == null) {
                    throw new ProviderException("Ollama response missing 'message' field", response.statusCode(), response.body());
                }

                OllamaChatMessage responseOllamaMsg = ollamaResponse.message();
                Message resultMessage;

                if (responseOllamaMsg.toolCalls() != null && !responseOllamaMsg.toolCalls().isEmpty()) {
                    List<ToolCallMessage.ToolCall> coreToolCalls = new ArrayList<>();
                    for (OllamaToolCall ollamaTc : responseOllamaMsg.toolCalls()) {
                        if ("function".equals(ollamaTc.type())) {
                            String argsJson = jsonObjectMapper.writeValueAsString(ollamaTc.function().arguments());
                            String toolCallId = ollamaTc.id() != null ? ollamaTc.id() :
                                "ollama-tc-" + System.currentTimeMillis() + "-" + coreToolCalls.size();
                            coreToolCalls.add(new ToolCallMessage.ToolCall(
                                toolCallId, ollamaTc.type(),
                                new ToolCallMessage.FunctionCall(ollamaTc.function().name(), argsJson)
                            ));
                        }
                    }

                    String toolCallMsgId = "ollama-tcm-" + System.currentTimeMillis();
                    resultMessage = new AssistantMessage(new ToolCallMessage(toolCallMsgId, coreToolCalls));
                    if (responseOllamaMsg.content() != null && !responseOllamaMsg.content().isEmpty()) {
                        resultMessage.addMetadata("preamble_text", responseOllamaMsg.content());
                    }
                } else {
                    resultMessage = new AssistantMessage(responseOllamaMsg.content());
                }

                resultMessage.setRole(MessageRole.ASSISTANT);

                // Add usage information
                if (ollamaResponse.promptEvalCount() != null || ollamaResponse.evalCount() != null) {
                    Usage usage = new Usage(
                        ollamaResponse.promptEvalCount() != null ? ollamaResponse.promptEvalCount() : 0,
                        ollamaResponse.evalCount() != null ? ollamaResponse.evalCount() : 0,
                        (ollamaResponse.promptEvalCount() != null ? ollamaResponse.promptEvalCount() : 0) +
                        (ollamaResponse.evalCount() != null ? ollamaResponse.evalCount() : 0)
                    );
                    resultMessage.setUsage(usage);
                }

                return resultMessage;

            } catch (Exception e) {
                throw new ProviderException("Error during Ollama API call: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public Message chat(List<Message> messages) {
        try {
            return this.chatAsync(messages, this.systemPromptText, new ArrayList<>(this.tools)).join();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new ProviderException("Error in synchronous Ollama chat: " + cause.getMessage(), cause);
        }
    }

    @Override
    public Stream<String> stream(List<Message> messages, String instructions, List<Object> toolObjects) {
        try {
            streamPromptEvalCount.set(0);
            streamEvalCount.set(0);

            String effectiveSystem = determineEffectiveSystemPrompt(instructions, messages);
            List<Message> messagesForMapping = messages.stream()
                .filter(msg -> msg.getRole() != MessageRole.SYSTEM)
                .collect(Collectors.toList());
            List<Map<String, Object>> mappedMessages = this.messageMapper.map(messagesForMapping);

            Map<String, Object> requestPayload = new HashMap<>();
            requestPayload.put("model", this.modelName);
            requestPayload.put("messages", mappedMessages);
            if (effectiveSystem != null && !effectiveSystem.isEmpty()) {
                requestPayload.put("system", effectiveSystem);
            }
            requestPayload.put("stream", true);

            List<com.skanga.tools.Tool> currentTools = new ArrayList<>();
            if (toolObjects != null) {
                for (Object obj : toolObjects) {
                    if (obj instanceof com.skanga.tools.Tool) {
                        currentTools.add((com.skanga.tools.Tool) obj);
                    }
                }
            }

            List<Map<String, Object>> ollamaToolsPayload = generateToolsPayload(currentTools);
            if (ollamaToolsPayload != null && !ollamaToolsPayload.isEmpty()) {
                requestPayload.put("tools", ollamaToolsPayload);
            }

            // Handle Ollama-specific parameters
            if (this.parameters.containsKey("format")) {
                requestPayload.put("format", this.parameters.get("format"));
            }
            if (this.parameters.containsKey("options")) {
                requestPayload.put("options", this.parameters.get("options"));
            }
            if (this.parameters.containsKey("keep_alive")) {
                requestPayload.put("keep_alive", this.parameters.get("keep_alive"));
            }

            String requestUrl = this.baseUrl + "/api/chat";
            String payloadJson = jsonObjectMapper.writeValueAsString(requestPayload);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                .build();

            HttpResponse<String> response = HttpClientManager.getSharedClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ProviderException("Ollama stream request failed", response.statusCode(), response.body());
            }

            return parseOllamaStream(response.body());

        } catch (Exception e) {
            throw new ProviderException("Error setting up Ollama stream: " + e.getMessage(), e);
        }
    }

    private Stream<String> parseOllamaStream(String responseBody) {
        BufferedReader reader = new BufferedReader(new StringReader(responseBody));
        List<String> textChunks = new ArrayList<>();

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                try {
                    OllamaChatResponse responseChunk = jsonObjectMapper.readValue(line, OllamaChatResponse.class);

                    if (responseChunk.message() != null && responseChunk.message().content() != null) {
                        String content = responseChunk.message().content();
                        if (!content.isEmpty()) {
                            textChunks.add(content);
                        }
                    }

                    if (responseChunk.done() != null && responseChunk.done()) {
                        if (responseChunk.promptEvalCount() != null) {
                            streamPromptEvalCount.set(responseChunk.promptEvalCount());
                        }
                        if (responseChunk.evalCount() != null) {
                            streamEvalCount.set(responseChunk.evalCount());
                        }
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing Ollama stream chunk: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new ProviderException("Error reading Ollama stream", e);
        }

        return textChunks.stream();
    }

    @Override
    public <T> T structured(List<Message> messages, Class<T> responseClass, Map<String, Object> responseSchema) {
        try {
            String schemaJson = jsonObjectMapper.writeValueAsString(responseSchema);
            String structuredInstructions = "You must respond with valid JSON that matches this exact schema: " + schemaJson +
                ". Do not include any text outside the JSON response.";

            String effectiveSystemPrompt = determineEffectiveSystemPrompt(null, messages);
            String finalSystemPrompt;
            if (effectiveSystemPrompt != null && !effectiveSystemPrompt.isEmpty()) {
                finalSystemPrompt = effectiveSystemPrompt + "\n\n" + structuredInstructions;
            } else {
                finalSystemPrompt = structuredInstructions;
            }

            List<Map<String, Object>> mappedMessages = this.messageMapper.map(messages);
            Map<String, Object> requestPayload = new HashMap<>();
            requestPayload.put("model", this.modelName);
            requestPayload.put("messages", mappedMessages);
            requestPayload.put("system", finalSystemPrompt);
            requestPayload.put("format", "json"); // Crucial for Ollama's JSON mode
            requestPayload.put("stream", false);

            if (this.parameters != null && this.parameters.containsKey("options")) {
                requestPayload.put("options", this.parameters.get("options"));
            }
            if (this.parameters != null && this.parameters.containsKey("keep_alive")) {
                requestPayload.put("keep_alive", this.parameters.get("keep_alive"));
            }

            String requestUrl = this.baseUrl + "/api/chat";
            String payloadJson = jsonObjectMapper.writeValueAsString(requestPayload);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                .build();

            HttpResponse<String> response = HttpClientManager.getSharedClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ProviderException("Ollama structured output request failed", response.statusCode(), response.body());
            }

            OllamaChatResponse ollamaResponse = jsonObjectMapper.readValue(response.body(), OllamaChatResponse.class);

            if (ollamaResponse.message() == null || ollamaResponse.message().content() == null) {
                throw new ProviderException("Ollama structured response missing message content", response.statusCode(), response.body());
            }

            String jsonContentString = ollamaResponse.message().content();

            try {
                // Validate JSON and parse to target class
                jsonObjectMapper.readTree(jsonContentString); // Validate JSON
                return jsonObjectMapper.readValue(jsonContentString, responseClass);
            } catch (Exception jsonEx) {
                throw new ProviderException("Ollama response was not valid JSON or did not match target class '" +
                    responseClass.getName() + "'. Content: " + jsonContentString, jsonEx, response.statusCode(), jsonContentString);
            }

        } catch (IOException | InterruptedException e) {
            throw new ProviderException("Error during Ollama structured output: " + e.getMessage(), e);
        }
    }
}

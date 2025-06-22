
// openai/OpenAIProvider.java
package com.skanga.providers.openai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.chat.enums.MessageRole;
import com.skanga.chat.messages.Message;
import com.skanga.providers.AIProvider;
import com.skanga.core.Usage;
import com.skanga.core.exceptions.ProviderException;
import com.skanga.core.messages.ToolCallMessage;
import com.skanga.providers.HttpClientManager;
import com.skanga.providers.ProviderUtils;
import com.skanga.providers.mappers.MessageMapper;
import com.skanga.providers.openai.dto.OpenAIStreamData;

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
import java.util.stream.Stream;

/**
 * An implementation of the {@link AIProvider} interface for interacting with OpenAI's API.
 * This provider supports chat completions, streaming, structured output (via JSON mode),
 * and tool calling.
 *
 * It uses JDK's HttpClient for synchronous HTTP communication internally for chat/structured,
 * and for initiating the stream. The actual stream reading uses Java's standard IO classes.
 *
 * Key features:
 * - Maps generic {@link Message} objects to OpenAI's format via {@link OpenAIMessageMapper}.
 * - Handles system prompts, user messages, assistant messages, and tool messages.
 * - Supports streaming responses for chat.
 * - Implements structured output using OpenAI's "JSON mode".
 * - Implements tool calling by formatting {@link com.skanga.tools.Tool} definitions
 *   into OpenAI's `tools` and `tool_choice` parameters and parsing `tool_calls` from responses.
 */
public class OpenAIProvider implements AIProvider {
    private static final String DEFAULT_BASE_URI = "https://api.openai.com/v1";

    private final String apiKey;
    private final String model;
    protected final String baseUri;
    private final Map<String, Object> parameters;
    private final List<Object> tools = new ArrayList<>();
    private final OpenAIMessageMapper messageMapper;
    private static final ObjectMapper jsonObjectMapper = ProviderUtils.getObjectMapper();

    private String systemPrompt;

    /**
     * Constructs an OpenAIProvider with a custom base URI.
     *
     * @param apiKey     Your OpenAI API key.
     * @param model      The OpenAI model name to use (e.g., "gpt-3.5-turbo", "gpt-4").
     * @param parameters Default parameters for API requests (e.g., temperature, max_tokens).
     * @param baseUri    The base URI for the OpenAI API. Allows overriding for proxies or different environments.
     */
    public OpenAIProvider(String apiKey, String model, Map<String, Object> parameters, String baseUri) {
        this.apiKey = Objects.requireNonNull(apiKey, "OpenAI API key cannot be null");
        this.model = Objects.requireNonNull(model, "OpenAI model name cannot be null");
        this.parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
        this.messageMapper = new OpenAIMessageMapper();
        this.baseUri = Objects.requireNonNull(baseUri, "Base URI cannot be null");
    }

    /**
     * Constructs an OpenAIProvider with the default OpenAI API base URI.
     *
     * @param apiKey     Your OpenAI API key.
     * @param model      The OpenAI model name to use.
     * @param parameters Default parameters for API requests.
     */
    public OpenAIProvider(String apiKey, String model, Map<String, Object> parameters) {
        this(apiKey, model, parameters, DEFAULT_BASE_URI);
    }

    public OpenAIProvider(String apiKey, String model, String baseUri) {
        this(apiKey, model, null, baseUri);
    }

    @Override
    public AIProvider systemPrompt(String prompt) {
        this.systemPrompt = prompt;
        return this;
    }

    @Override
    public AIProvider setTools(List<Object> tools) {
        this.tools.clear();
        if (tools != null) {
            this.tools.addAll(tools);
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

    @Override
    public Message chat(List<Message> messages) {
        try {
            // Uses provider's currently configured systemPrompt and tools list (this.tools)
            return this.chatAsync(messages, this.systemPrompt, this.tools).join();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new ProviderException("Error in OpenAI synchronous chat: " + cause.getMessage(), cause);
        }
    }

    @Override
    public CompletableFuture<Message> chatAsync(List<Message> messages, String instructions, List<Object> toolObjects) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Message> allMessages = new ArrayList<>();

                String currentInstructions = instructions;
                if (currentInstructions == null && this.systemPrompt != null && !this.systemPrompt.isEmpty()) {
                    currentInstructions = this.systemPrompt;
                }

                if (currentInstructions != null && !currentInstructions.isEmpty()) {
                    allMessages.add(new Message(MessageRole.SYSTEM, currentInstructions));
                }

                allMessages.addAll(messages);

                List<com.skanga.tools.Tool> currentTools = new ArrayList<>();
                if (toolObjects != null) {
                    for (Object obj : toolObjects) {
                        if (obj instanceof com.skanga.tools.Tool) {
                            currentTools.add((com.skanga.tools.Tool) obj);
                        } else {
                            System.err.println("Warning: Tool object is not com.skanga.tools.Tool: " +
                                (obj != null ? obj.getClass().getName() : "null"));
                        }
                    }
                }

                List<Map<String, Object>> mappedMessages = this.messageMapper.map(allMessages);
                Map<String, Object> requestPayload = new HashMap<>();
                requestPayload.put("model", this.model);
                requestPayload.put("messages", mappedMessages);

                Map<String, Object> effectiveParams = new HashMap<>(this.parameters);
                effectiveParams.putIfAbsent("stream", false);
                requestPayload.putAll(effectiveParams);

                List<Map<String, Object>> toolsPayload = generateToolsPayload(currentTools);
                if (toolsPayload != null && !toolsPayload.isEmpty()) {
                    requestPayload.put("tools", toolsPayload);
                    requestPayload.put("tool_choice", "auto");
                }

                String payloadJson = jsonObjectMapper.writeValueAsString(requestPayload);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUri + "/chat/completions"))
                    .header("Authorization", "Bearer " + this.apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(2))
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                    .build();

                HttpResponse<String> response = HttpClientManager.getSharedClient().send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new ProviderException("OpenAI API request failed", response.statusCode(), response.body());
                }

                Map<String, Object> responseMap = jsonObjectMapper.readValue(response.body(), new TypeReference<>() {});

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices == null || choices.isEmpty()) {
                    throw new ProviderException("OpenAI response missing 'choices'", response.statusCode(), response.body());
                }

                Map<String, Object> firstChoice = choices.get(0);
                @SuppressWarnings("unchecked")
                Map<String, Object> messageData = (Map<String, Object>) firstChoice.get("message");

                if (messageData == null) {
                    throw new ProviderException("OpenAI response choice missing 'message'", response.statusCode(), response.body());
                }

                String roleStr = ProviderUtils.getStringValue(messageData, "role", "assistant");
                MessageRole role = MessageRole.valueOf(roleStr.toUpperCase());

                Message resultMessage;

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> toolCallsData = (List<Map<String, Object>>) messageData.get("tool_calls");
                if (toolCallsData != null && !toolCallsData.isEmpty()) {
                    ToolCallMessage toolCallMessage = createToolCallMessage(toolCallsData);
                    resultMessage = new Message(role, toolCallMessage);
                } else {
                    String content = ProviderUtils.getStringValue(messageData, "content", "");
                    resultMessage = new Message(role, content);
                }

                // Add usage information
                @SuppressWarnings("unchecked")
                Map<String, Integer> usageData = (Map<String, Integer>) responseMap.get("usage");
                if (usageData != null) {
                    Usage usage = new Usage(usageData.get("prompt_tokens"),
                        usageData.get( "completion_tokens"),
                        usageData.get( "total_tokens")
                    );
                    resultMessage.setUsage(usage);
                }

                return resultMessage;

            } catch (Exception e) {
                throw new ProviderException("Error during OpenAI API call: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public Stream<String> stream(List<Message> messages, String instructions, List<Object> toolObjects) {
        try {
            List<Message> allMessages = new ArrayList<>();

            String currentInstructions = instructions;
            if (currentInstructions == null && this.systemPrompt != null && !this.systemPrompt.isEmpty()) {
                currentInstructions = this.systemPrompt;
            }

            if (currentInstructions != null && !currentInstructions.isEmpty()) {
                allMessages.add(new Message(MessageRole.SYSTEM, currentInstructions));
            }

            allMessages.addAll(messages);

            List<com.skanga.tools.Tool> currentTools = new ArrayList<>();
            if (toolObjects != null) {
                for (Object obj : toolObjects) {
                    if (obj instanceof com.skanga.tools.Tool) {
                        currentTools.add((com.skanga.tools.Tool) obj);
                    } else {
                        System.err.println("Warning: Tool object is not com.skanga.tools.Tool: " +
                            (obj != null ? obj.getClass().getName() : "null"));
                    }
                }
            }

            List<Map<String, Object>> mappedMessages = this.messageMapper.map(allMessages);
            Map<String, Object> requestPayload = new HashMap<>();
            requestPayload.put("model", this.model);
            requestPayload.put("messages", mappedMessages);
            requestPayload.put("stream", true);

            Map<String, Object> effectiveParams = new HashMap<>(this.parameters);
            effectiveParams.put("stream", true);
            requestPayload.putAll(effectiveParams);

            List<Map<String, Object>> toolsPayload = generateToolsPayload(currentTools);
            if (toolsPayload != null && !toolsPayload.isEmpty()) {
                requestPayload.put("tools", toolsPayload);
                requestPayload.put("tool_choice", "auto");
            }

            String payloadJson = jsonObjectMapper.writeValueAsString(requestPayload);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.baseUri + "/chat/completions"))
                .header("Authorization", "Bearer " + this.apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                .build();

            HttpResponse<String> response = HttpClientManager.getSharedClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ProviderException("OpenAI stream request failed", response.statusCode(), response.body());
            }

            return parseOpenAIStream(response.body());

        } catch (Exception e) {
            throw new ProviderException("Error setting up OpenAI stream: " + e.getMessage(), e);
        }
    }

    private Stream<String> parseOpenAIStream(String responseBody) {
        BufferedReader reader = new BufferedReader(new StringReader(responseBody));
        List<String> textChunks = new ArrayList<>();

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String jsonData = line.substring(6).trim();
                    if ("[DONE]".equals(jsonData)) {
                        break;
                    }

                    try {
                        OpenAIStreamData streamData = jsonObjectMapper.readValue(jsonData, OpenAIStreamData.class);
                        if (streamData.choices() != null && !streamData.choices().isEmpty()) {
                            if (streamData.choices().get(0).delta() != null &&
                                streamData.choices().get(0).delta().content() != null) {
                                textChunks.add(streamData.choices().get(0).delta().content());
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing OpenAI stream chunk: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            throw new ProviderException("Error reading OpenAI stream", e);
        }

        return textChunks.stream();
    }

    @Override
    public <T> T structured(List<Message> messages, Class<T> responseClass, Map<String, Object> responseSchema) {
        try {
            String schemaJson = jsonObjectMapper.writeValueAsString(responseSchema);
            String schemaInstructions = "You must respond with valid JSON that matches this exact schema: " + schemaJson;

            List<Message> allMessages = new ArrayList<>();
            allMessages.add(new Message(MessageRole.SYSTEM, schemaInstructions));

            if (this.systemPrompt != null && !this.systemPrompt.isEmpty()) {
                allMessages.add(new Message(MessageRole.SYSTEM, this.systemPrompt));
            }

            allMessages.addAll(messages);

            List<Map<String, Object>> mappedMessages = this.messageMapper.map(allMessages);
            Map<String, Object> requestPayload = new HashMap<>();
            requestPayload.put("model", this.model);
            requestPayload.put("messages", mappedMessages);
            requestPayload.put("response_format", Map.of("type", "json_object"));

            Map<String, Object> effectiveParameters = new HashMap<>(this.parameters);
            effectiveParameters.remove("stream");
            requestPayload.putAll(effectiveParameters);

            List<com.skanga.tools.Tool> providerTools = new ArrayList<>();
            for (Object obj : this.tools) {
                if (obj instanceof com.skanga.tools.Tool) {
                    providerTools.add((com.skanga.tools.Tool) obj);
                } else {
                    System.err.println("Warning: Tool object in provider tools is not com.skanga.tools.Tool: " +
                        (obj != null ? obj.getClass().getName() : "null"));
                }
            }

            List<Map<String, Object>> toolsPayload = generateToolsPayload(providerTools);
            if (toolsPayload != null && !toolsPayload.isEmpty()) {
                requestPayload.put("tools", toolsPayload);
            }

            String payloadJson = jsonObjectMapper.writeValueAsString(requestPayload);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.baseUri + "/chat/completions"))
                .header("Authorization", "Bearer " + this.apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                .build();

            HttpResponse<String> response = HttpClientManager.getSharedClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ProviderException("OpenAI structured request failed", response.statusCode(), response.body());
            }

            if (response.body() == null || response.body().trim().isEmpty()) {
                throw new ProviderException("OpenAI structured response is empty", response.statusCode(), response.body());
            }

            Map<String, Object> responseMap = jsonObjectMapper.readValue(response.body(), new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new ProviderException("OpenAI structured response missing 'choices'", response.statusCode(), response.body());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> messageData = (Map<String, Object>) choices.get(0).get("message");
            if (messageData == null) {
                throw new ProviderException("OpenAI structured response missing 'message'", response.statusCode(), response.body());
            }

            String jsonContent = ProviderUtils.getStringValue(messageData, "content", "");
            if (jsonContent.trim().isEmpty()) {
                throw new ProviderException("OpenAI structured response content is empty", response.statusCode(), response.body());
            }

            return jsonObjectMapper.readValue(jsonContent, responseClass);

        } catch (Exception e) {
            throw new ProviderException("Error during OpenAI structured output: " + e.getMessage(), e);
        }
    }

    /**
     * Generates the payload for tools in the format expected by OpenAI API.
     *
     * @param tools A list of {@link com.skanga.tools.Tool} instances.
     * @return A list of maps, where each map represents a tool formatted for OpenAI,
     *         or null if the input list is null or empty.
     */
    private List<Map<String, Object>> generateToolsPayload(List<com.skanga.tools.Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }

        List<Map<String, Object>> toolsPayloadList = new ArrayList<>();
        for (com.skanga.tools.Tool tool : tools) {
            Map<String, Object> toolMap = new HashMap<>();
            toolMap.put("type", "function");

            Map<String, Object> functionDetails = new HashMap<>();
            functionDetails.put("name", tool.getName());
            functionDetails.put("description", tool.getDescription());
            // tool.getJsonSchema() should return the parameters schema for the function
            // which is an object schema with "type": "object", "properties": {...}, "required": [...]
            Map<String, Object> parametersSchema = tool.getJsonSchema();
            functionDetails.put("parameters", parametersSchema);

            toolMap.put("function", functionDetails);
            toolsPayloadList.add(toolMap);
        }
        return toolsPayloadList;
    }

    /**
     * Parses OpenAI's `tool_calls` array from the API response and creates
     * an internal {@link ToolCallMessage} object.
     *
     * @param openAiToolCalls The list of tool call maps from OpenAI's response.
     * @return A {@link ToolCallMessage} instance, or null if no valid tool calls were parsed.
     */
    private ToolCallMessage createToolCallMessage(List<Map<String, Object>> openAiToolCalls) {
        List<ToolCallMessage.ToolCall> ourToolCalls = new ArrayList<>();
        // Generate a unique ID for the overarching ToolCallMessage that groups these individual calls.
        String overarchingToolCallMessageId = "tcm-" + System.currentTimeMillis();

        for (Map<String, Object> openAiTc : openAiToolCalls) {
            String type = ProviderUtils.getStringValue(openAiTc, "type", "");
            if (!"function".equals(type)) {
                System.err.println("Warning: Unhandled tool call type from OpenAI: " + type);
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> functionMap = (Map<String, Object>) openAiTc.get("function");
            if (functionMap == null) {
                System.err.println("Warning: Function tool call missing function details");
                continue;
            }

            String callId = ProviderUtils.getStringValue(openAiTc, "id", "");
            String funcName = ProviderUtils.getStringValue(functionMap, "name", "");
            String funcArgs = ProviderUtils.getStringValue(functionMap, "arguments", "");

            if (!callId.isEmpty() && !funcName.isEmpty()) {
                ourToolCalls.add(new ToolCallMessage.ToolCall(
                    callId,
                    type,
                    new ToolCallMessage.FunctionCall(funcName, funcArgs)
                ));
            } else {
                System.err.println("Warning: Missing required fields in OpenAI tool call");
            }
        }

        if (ourToolCalls.isEmpty()) {
            // This could happen if openAiToolCalls was non-empty but contained no valid/parseable function calls.
            return null;
        }
        return new ToolCallMessage(overarchingToolCallMessageId, ourToolCalls);
    }
}

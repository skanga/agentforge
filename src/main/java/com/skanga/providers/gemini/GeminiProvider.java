
// gemini/GeminiProvider.java
package com.skanga.providers.gemini;

import com.fasterxml.jackson.core.type.TypeReference;
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
import com.skanga.providers.gemini.dto.*;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class GeminiProvider implements AIProvider {
    private static final String DEFAULT_BASE_URI = "https://generativelanguage.googleapis.com/v1beta/models";

    private final String apiKey;
    private final String modelName;
    private final String baseUri;
    private final Map<String, Object> parameters;
    private final List<Object> tools = new ArrayList<>();
    private final GeminiMessageMapper messageMapper;
    private static final ObjectMapper jsonObjectMapper = ProviderUtils.getObjectMapper();
    private final AtomicInteger streamPromptTokenCount = new AtomicInteger(0);
    private final AtomicInteger streamCandidatesTokenCount = new AtomicInteger(0);

    private String systemInstructionText;

    public GeminiProvider(String apiKey, String modelName, Map<String, Object> parameters, String baseUri) {
        this.apiKey = Objects.requireNonNull(apiKey, "API key cannot be null");
        this.modelName = Objects.requireNonNull(modelName, "Model name cannot be null");
        this.baseUri = baseUri != null ? baseUri : DEFAULT_BASE_URI;
        this.parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
        this.messageMapper = new GeminiMessageMapper();
    }

    public GeminiProvider(String apiKey, String modelName, Map<String, Object> parameters) {
        this(apiKey, modelName, parameters, null);
    }

    public GeminiProvider(String apiKey, String modelName) {
        this(apiKey, modelName, null, null);
    }

    @Override
    public AIProvider systemPrompt(String prompt) {
        this.systemInstructionText = prompt;
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

        List<GeminiFunctionDeclaration> functionDeclarations = tools.stream()
            .map(tool -> new GeminiFunctionDeclaration(
                tool.getName(),
                tool.getDescription(),
                tool.getJsonSchema()
            ))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        if (!functionDeclarations.isEmpty()) {
            List<Map<String, Object>> toolsListWrapper = new ArrayList<>();
            Map<String, Object> geminiToolObject = new HashMap<>();
            geminiToolObject.put("function_declarations", functionDeclarations.stream()
                .map(fd -> jsonObjectMapper.convertValue(fd, new TypeReference<Map<String, Object>>() {}))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll));
            toolsListWrapper.add(geminiToolObject);
            return toolsListWrapper;
        }
        return null;
    }

    private String determineEffectiveSystemInstruction(String instructionsParam, List<Message> messages) {
        String effectiveSystemInstruction = instructionsParam;
        if (effectiveSystemInstruction == null) {
            effectiveSystemInstruction = this.systemInstructionText;
        }

        // Check for system messages in the conversation
        for (Message msg : messages) {
            if (msg.getRole() == MessageRole.SYSTEM && msg.getContent() instanceof String) {
                effectiveSystemInstruction = (String) msg.getContent();
                break; // Use the first system message found
            }
        }
        return effectiveSystemInstruction;
    }


    @Override
    public CompletableFuture<Message> chatAsync(List<Message> messages, String instructions, List<Object> toolObjects) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String effectiveSystemInstructionText = determineEffectiveSystemInstruction(instructions, messages);

                List<Message> messagesForContents = messages.stream()
                    .filter(msg -> msg.getRole() != MessageRole.SYSTEM)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

                List<Map<String, Object>> mappedContents = this.messageMapper.map(messagesForContents);
                Map<String, Object> requestPayload = new HashMap<>();
                requestPayload.put("contents", mappedContents);

                if (effectiveSystemInstructionText != null && !effectiveSystemInstructionText.isEmpty()) {
                    GeminiSystemInstruction systemInstruction = new GeminiSystemInstruction(effectiveSystemInstructionText);
                    requestPayload.put("system_instruction",
                        jsonObjectMapper.convertValue(systemInstruction, new TypeReference<Map<String, Object>>() {}));
                }

                List<com.skanga.tools.Tool> currentTools = new ArrayList<>();
                if (toolObjects != null) {
                    for (Object obj : toolObjects) {
                        if (obj instanceof com.skanga.tools.Tool) {
                            currentTools.add((com.skanga.tools.Tool) obj);
                        }
                    }
                }

                List<Map<String, Object>> geminiToolsPayload = generateToolsPayload(currentTools);
                if (geminiToolsPayload != null && !geminiToolsPayload.isEmpty()) {
                    requestPayload.put("tools", geminiToolsPayload);
                }

                if (this.parameters != null && !this.parameters.isEmpty()) {
                    requestPayload.put("generationConfig", this.parameters);
                }

                String requestUrl = String.format("%s/%s:generateContent", this.baseUri, this.modelName);
                String payloadJson = jsonObjectMapper.writeValueAsString(requestPayload);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("x-goog-api-key", this.apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(2))
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                    .build();

                HttpResponse<String> response = HttpClientManager.getSharedClient().send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new ProviderException("Gemini API request failed", response.statusCode(), response.body());
                }

                GenerateContentResponse geminiResponse = jsonObjectMapper.readValue(response.body(), GenerateContentResponse.class);

                if (geminiResponse.candidates() == null || geminiResponse.candidates().isEmpty()) {
                    if (geminiResponse.promptFeedback() != null && geminiResponse.promptFeedback().blockReason() != null) {
                        throw new ProviderException("Gemini prompt was blocked: " + geminiResponse.promptFeedback().blockReason() +
                            (geminiResponse.promptFeedback().safetyRatings() != null ? " SafetyRatings: " + geminiResponse.promptFeedback().safetyRatings() : ""),
                            response.statusCode(), response.body());
                    }
                    throw new ProviderException("Gemini response has no candidates", response.statusCode(), response.body());
                }

                GeminiCandidate firstCandidate = geminiResponse.candidates().get(0);
                if (firstCandidate.content() == null || firstCandidate.content().parts() == null || firstCandidate.content().parts().isEmpty()) {
                    if (firstCandidate.finishReason() != null && !"STOP".equals(firstCandidate.finishReason())) {
                        throw new ProviderException("Gemini candidate finished due to: " + firstCandidate.finishReason() +
                            (firstCandidate.safetyRatings() != null ? " SafetyRatings: " + firstCandidate.safetyRatings() : ""),
                            response.statusCode(), response.body());
                    }
                    return new AssistantMessage("");
                }

                StringBuilder textContentBuilder = new StringBuilder(1024);
                List<ToolCallMessage.ToolCall> requestedToolCalls = new ArrayList<>();

                for (GeminiPart part : firstCandidate.content().parts()) {
                    if (part.text() != null) {
                        textContentBuilder.append(part.text());
                    } else if (part.functionCall() != null) {
                        GeminiFunctionCall fc = part.functionCall();
                        String argsJson = jsonObjectMapper.writeValueAsString(fc.args());
                        String toolCallId = "gemini-fc-" + System.currentTimeMillis() + "-" + requestedToolCalls.size();
                        requestedToolCalls.add(new ToolCallMessage.ToolCall(
                            toolCallId, "function",
                            new ToolCallMessage.FunctionCall(fc.name(), argsJson)
                        ));
                    }
                }

                Message resultMessage;
                if (!requestedToolCalls.isEmpty()) {
                    String toolCallMsgId = "gemini-tcm-" + System.currentTimeMillis();
                    resultMessage = new AssistantMessage(new ToolCallMessage(toolCallMsgId, requestedToolCalls));
                    if (textContentBuilder.length() > 0) {
                        resultMessage.addMetadata("preamble_text", textContentBuilder.toString());
                    }
                } else {
                    resultMessage = new AssistantMessage(textContentBuilder.toString());
                }

                resultMessage.setRole(MessageRole.ASSISTANT);

                // Add usage information
                if (geminiResponse.usageMetadata() != null) {
                    GeminiUsageMetadata usageData = geminiResponse.usageMetadata();
                    Usage usage = new Usage(
                        usageData.promptTokenCount() != null ? usageData.promptTokenCount() : 0,
                        usageData.candidatesTokenCount() != null ? usageData.candidatesTokenCount() : 0,
                        usageData.totalTokenCount() != null ? usageData.totalTokenCount() : 0
                    );
                    resultMessage.setUsage(usage);
                }
                return resultMessage;

            } catch (Exception e) {
                throw new ProviderException("Error during Gemini API call: " + e.getMessage(), e);
            }
        });
    }


    @Override
    public Message chat(List<Message> messages) {
         try {
            return this.chatAsync(messages, this.systemInstructionText, new ArrayList<>(this.tools)).join();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new ProviderException("Error in synchronous Gemini chat: " + cause.getMessage(), cause);
        }
    }

    @Override
    public Stream<String> stream(List<Message> messages, String instructions, List<Object> toolObjects) {
        try {
            streamPromptTokenCount.set(0);
            streamCandidatesTokenCount.set(0);

            String effectiveSystemInstructionText = determineEffectiveSystemInstruction(instructions, messages);

            List<Message> messagesForContents = messages.stream()
                .filter(msg -> msg.getRole() != MessageRole.SYSTEM)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

            List<Map<String, Object>> mappedContents = this.messageMapper.map(messagesForContents);
            Map<String, Object> requestPayload = new HashMap<>();
            requestPayload.put("contents", mappedContents);

            if (effectiveSystemInstructionText != null && !effectiveSystemInstructionText.isEmpty()) {
                requestPayload.put("system_instruction",
                    jsonObjectMapper.convertValue(new GeminiSystemInstruction(effectiveSystemInstructionText),
                    new TypeReference<Map<String, Object>>() {}));
            }

            List<com.skanga.tools.Tool> currentTools = new ArrayList<>();
            if (toolObjects != null) {
                for (Object obj : toolObjects) {
                    if (obj instanceof com.skanga.tools.Tool) {
                        currentTools.add((com.skanga.tools.Tool) obj);
                    }
                }
            }

            List<Map<String, Object>> geminiToolsPayload = generateToolsPayload(currentTools);
            if (geminiToolsPayload != null && !geminiToolsPayload.isEmpty()) {
                requestPayload.put("tools", geminiToolsPayload);
            }

            if (this.parameters != null && !this.parameters.isEmpty()) {
                requestPayload.put("generationConfig", this.parameters);
            }

            String requestUrl = String.format("%s/%s:streamGenerateContent?alt=sse", this.baseUri, this.modelName);
            String payloadJson = jsonObjectMapper.writeValueAsString(requestPayload);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .header("x-goog-api-key", this.apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                .build();

            HttpResponse<String> response = HttpClientManager.getSharedClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ProviderException("Gemini stream request failed", response.statusCode(), response.body());
            }

            return parseGeminiStream(response.body());

        } catch (Exception e) {
            throw new ProviderException("Error setting up Gemini stream: " + e.getMessage(), e);
        }
    }

    private Stream<String> parseGeminiStream(String responseBody) {
        BufferedReader reader = new BufferedReader(new StringReader(responseBody));
        List<String> textChunks = new ArrayList<>();

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String jsonData = line.substring(5).trim();
                    if (jsonData.isEmpty()) continue;

                    try {
                        GenerateContentResponse responseChunk = jsonObjectMapper.readValue(jsonData, GenerateContentResponse.class);

                        if (responseChunk.usageMetadata() != null) {
                            if (responseChunk.usageMetadata().promptTokenCount() != null) {
                                streamPromptTokenCount.compareAndSet(0, responseChunk.usageMetadata().promptTokenCount());
                            }
                        }

                        if (responseChunk.candidates() != null && !responseChunk.candidates().isEmpty()) {
                            GeminiCandidate candidate = responseChunk.candidates().get(0);
                            if (candidate.content() != null && candidate.content().parts() != null) {
                                StringBuilder currentChunkText = new StringBuilder(responseBody.length());
                                for (GeminiPart part : candidate.content().parts()) {
                                    if (part.text() != null) {
                                        currentChunkText.append(part.text());
                                    }
                                }

                                if (currentChunkText.length() > 0) {
                                    textChunks.add(currentChunkText.toString());
                                }
                            }

                            if (candidate.finishReason() != null &&
                                !"NOT_SPECIFIED".equals(candidate.finishReason()) &&
                                !"STOP".equals(candidate.finishReason())) {
                                break; // Stream finished for non-STOP reasons
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing Gemini stream chunk: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            throw new ProviderException("Error reading Gemini stream", e);
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
            List<Map<String, Object>> geminiToolsPayload = generateToolsPayload(toolsForRequest);

            String augmentedSystemInstruction = "You must use the " + dynamicToolName + " tool to provide your response. " +
                "Extract the requested information and call the tool with the structured data.";

            String effectiveSystemInstructionText = determineEffectiveSystemInstruction(augmentedSystemInstruction, messages);

            List<Message> messagesForContents = messages.stream()
                .filter(msg -> msg.getRole() != MessageRole.SYSTEM)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

            List<Map<String, Object>> mappedContents = this.messageMapper.map(messagesForContents);
            Map<String, Object> requestPayload = new HashMap<>();
            requestPayload.put("contents", mappedContents);

            if (effectiveSystemInstructionText != null && !effectiveSystemInstructionText.isEmpty()) {
                requestPayload.put("system_instruction",
                    jsonObjectMapper.convertValue(new GeminiSystemInstruction(effectiveSystemInstructionText),
                    new TypeReference<Map<String, Object>>() {}));
            }

            if (geminiToolsPayload != null && !geminiToolsPayload.isEmpty()) {
                requestPayload.put("tools", geminiToolsPayload);

                Map<String, Object> functionCallingConfig = new HashMap<>();
                functionCallingConfig.put("mode", "ANY");
                requestPayload.put("tool_config", Map.of("function_calling_config", functionCallingConfig));
            } else {
                throw new ProviderException("Dynamic tool for Gemini structured output could not be constructed");
            }

            if (this.parameters != null && !this.parameters.isEmpty()) {
                requestPayload.put("generationConfig", this.parameters);
            }

            String requestUrl = String.format("%s/%s:generateContent", this.baseUri, this.modelName);
            String payloadJson = jsonObjectMapper.writeValueAsString(requestPayload);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .header("x-goog-api-key", this.apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                .build();

            HttpResponse<String> response = HttpClientManager.getSharedClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ProviderException("Gemini structured output request failed", response.statusCode(), response.body());
            }

            GenerateContentResponse geminiResponse = jsonObjectMapper.readValue(response.body(), GenerateContentResponse.class);

            if (geminiResponse.candidates() == null || geminiResponse.candidates().isEmpty()) {
                throw new ProviderException("Gemini structured response has no candidates", response.statusCode(), response.body());
            }

            GeminiCandidate firstCandidate = geminiResponse.candidates().get(0);
            if (firstCandidate.content() != null && firstCandidate.content().parts() != null) {
                for (GeminiPart part : firstCandidate.content().parts()) {
                    if (part.functionCall() != null && dynamicToolName.equals(part.functionCall().name())) {
                        Map<String, Object> argsMap = part.functionCall().args();
                        String jsonArgsString = jsonObjectMapper.writeValueAsString(argsMap);
                        return jsonObjectMapper.readValue(jsonArgsString, responseClass);
                    }
                }
            }

            throw new ProviderException("Gemini response did not use required tool for structured output", response.statusCode(), response.body());

        } catch (Exception e) {
            throw new ProviderException("Error during Gemini structured output: " + e.getMessage(), e);
        }
    }
}
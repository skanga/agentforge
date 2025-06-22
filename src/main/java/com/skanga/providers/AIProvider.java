package com.skanga.providers;

import com.skanga.chat.messages.Message;
import com.skanga.providers.mappers.MessageMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Defines the interface for an AI Provider.
 * An AI Provider is responsible for interacting with a specific Large Language Model (LLM)
 * service (e.g., OpenAI, Anthropic, Gemini, Ollama). It handles request formatting, API calls,
 * response parsing, and error management for chat, streaming, and structured output.
 *
 * Implementations of this interface will be specific to each LLM provider's API.
 * All providers use the JDK's built-in HttpClient for HTTP communication.
 */
public interface AIProvider {
    /**
     * Performs an asynchronous chat completion request to the AI provider.
     *
     * @param messages     A list of {@link Message} objects representing the conversation history and current prompt.
     *                     The implementation will typically map these to the provider's specific message format.
     * @param instructions A system-level instruction string to guide the model's behavior for this specific call.
     *                     This might augment or override any system prompt set globally on the provider.
     * @param tools        A list of tool objects (e.g., {@link com.skanga.tools.Tool} instances)
     *                     available for the LLM to use. The provider will format these according to its API.
     *                     Passed as {@code List<Object>} for flexibility, concrete implementations will cast/process.
     * @return A {@link CompletableFuture} that will complete with the AI's {@link Message} response.
     *         The response message may contain text content or a request for tool execution
     *         (e.g., its content might be a {@link com.skanga.core.messages.ToolCallMessage}).
     * @throws com.skanga.core.exceptions.ProviderException if an error occurs during the API call or processing.
     */
    CompletableFuture<Message> chatAsync(List<Message> messages, String instructions, List<Object> tools);

    /**
     * Initiates a streaming chat completion request to the AI provider.
     *
     * @param messages     A list of {@link Message} objects.
     * @param instructions A system-level instruction string for this call.
     * @param tools        A list of available tool objects.
     * @return A {@link Stream} of {@link String} chunks representing the AI's response as it's generated.
     *         The stream handles proper resource management and parsing of server-sent events.
     * @throws com.skanga.core.exceptions.ProviderException if an error occurs initiating the stream.
     */
    Stream<String> stream(List<Message> messages, String instructions, List<Object> tools);

    /**
     * Performs a request to the AI provider aiming for a structured response that maps to a specific Java class.
     * The mechanism varies by provider:
     * - OpenAI/Deepseek/Mistral: Uses JSON mode with response_format
     * - Anthropic/Gemini: Uses dynamic tool creation with forced tool choice
     * - Ollama: Uses format parameter with "json"
     *
     * @param messages       A list of {@link Message} objects.
     * @param responseClass  The {@link Class} of the desired structured response type.
     * @param responseSchema A map representing the JSON schema that the AI's output should conform to.
     *                       This schema guides the AI in generating the structured data.
     * @param <T>            The type of the structured response.
     * @return An instance of {@code responseClass} (type T) populated with data from the AI.
     *         This is a synchronous/blocking call.
     * @throws com.skanga.core.exceptions.ProviderException if an error occurs or the response cannot be structured.
     */
    <T> T structured(List<Message> messages, Class<T> responseClass, Map<String, Object> responseSchema);

    /**
     * Sets a system-level prompt/instruction that will be used by default for interactions
     * with this provider, unless overridden by call-specific instructions.
     *
     * @param prompt The system prompt string.
     * @return The current AIProvider instance for fluent chaining.
     */
    AIProvider systemPrompt(String prompt);

    /**
     * Configures the set of tools available to the AI model for this provider.
     * Tools should implement the {@link com.skanga.tools.Tool} interface.
     *
     * @param tools A list of tool objects. Implementations will filter and cast these to their
     *              internal {@link com.skanga.tools.Tool} representation to extract schemas.
     *              Non-Tool objects will be logged as warnings and ignored.
     * @return The current AIProvider instance for fluent chaining.
     */
    AIProvider setTools(List<Object> tools);

    /**
     * Gets the {@link MessageMapper} associated with this provider.
     * The mapper is responsible for converting generic {@link Message} objects
     * into the specific format required by the provider's API.
     *
     * @return The {@link MessageMapper} instance.
     */
    MessageMapper messageMapper();

    /**
     * Sets the HTTP client to be used by this provider.
     *
     * Note: All providers now use the JDK's built-in HttpClient internally,
     * so this method is maintained for interface compatibility but may not
     * have any effect in the current implementation.
     *
     * @param client The HTTP client object. The type is Object to accommodate
     *               different client types, but implementations typically ignore this
     *               and use the standard JDK HttpClient.
     * @return The current AIProvider instance for fluent chaining.
     */
    AIProvider setHttpClient(Object client);

    /**
     * Performs a synchronous chat completion request.
     * This is a convenience method that uses the provider's currently configured
     * system prompt and tools, and blocks until a response is available.
     *
     * @param messages A list of {@link Message} objects for the conversation.
     * @return The AI's {@link Message} response.
     * @throws com.skanga.core.exceptions.ProviderException if an error occurs.
     */
    Message chat(List<Message> messages);
}
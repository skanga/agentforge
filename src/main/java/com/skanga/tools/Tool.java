package com.skanga.tools;

import com.skanga.tools.properties.ToolProperty;
import com.skanga.tools.properties.ToolPropertySchema;

import java.util.List;
import java.util.Map;

/**
 * Represents a tool that an AI agent can use.
 * A Tool is defined by its name, description, and a set of parameters it accepts.
 * It also encapsulates the logic to be executed when the tool is called.
 *
 * <p>A Tool itself also acts as a {@link ToolPropertySchema}, meaning its parameter definitions
 * (name, description, and the schema of its parameters) can be represented as a JSON schema.
 * This is crucial for AI models to understand how to use the tool (Function Calling / Tool Usage).</p>
 *
 * <p>The lifecycle of a tool typically involves:
 * <ol>
 *   <li>Definition: Creating an instance (e.g., of {@link BaseTool}) and defining its name,
 *       description, and parameters (using {@code addParameter}).</li>
 *   <li>Setting Callable: Assigning the actual execution logic via {@link #setCallable(ToolCallable)}.</li>
 *   <li>Schema Generation: The AI provider uses {@link #getJsonSchema()} (inherited from
 *       {@link ToolPropertySchema} and implemented by {@link BaseTool}) to get the tool's
 *       parameter schema to send to the LLM.</li>
 *   <li>Invocation by LLM: The LLM decides to use the tool and provides arguments.</li>
 *   <li>Execution by Agent: The agent framework receives the LLM's request, sets the
 *       {@link #setInputs(Map)} and {@link #setCallId(String)}, then calls {@link #executeCallable()}.</li>
 *   <li>Result Retrieval: The result is obtained via {@link #getResult()} and sent back to the LLM.</li>
 * </ol>
 */
public interface Tool extends ToolPropertySchema {

    /**
     * Gets the unique name of the tool.
     * This name is used by the AI model to identify and request the execution of this tool.
     * It should typically be short, descriptive, and follow naming conventions suitable for
     * function names (e.g., snake_case or camelCase).
     *
     * @return The tool's name.
     */
    String getName();

    /**
     * Gets a human-readable description of what the tool does, its purpose, and potentially
     * when it should be used. This description is provided to the AI model to help it
     * decide whether to use the tool.
     *
     * @return The tool's description.
     */
    String getDescription();

    /**
     * Gets the list of parameters that this tool accepts as input.
     * Each parameter is defined by a {@link ToolProperty} object, which includes its
     * name, type, description, and whether it's required.
     *
     * @return A list of {@link ToolProperty} objects defining the tool's input parameters.
     */
    List<ToolProperty> getParameters();

    /**
     * Gets a list of names of parameters that are required for this tool to execute.
     * This is derived from the `isRequired()` flag of each {@link ToolProperty} in {@link #getParameters()}.
     *
     * @return A list of names for required parameters.
     */
    List<String> getRequiredParameters();

    /**
     * Sets the executable logic for this tool.
     * The provided {@link ToolCallable} instance will be invoked when {@link #executeCallable()} is called.
     *
     * @param callable The {@link ToolCallable} instance that implements the tool's core functionality.
     */
    void setCallable(ToolCallable callable);

    /**
     * Gets the input arguments that have been set for a specific invocation of this tool.
     * This map is typically populated by the agent framework based on the arguments
     * provided by the AI model when it requests a tool call.
     *
     * @return A map of input argument names to their values.
     */
    Map<String, Object> getInputs();

    /**
     * Sets the input arguments for a specific invocation of this tool.
     * This method is usually called by the agent framework before {@link #executeCallable()}.
     *
     * @param inputs A map where keys are argument names and values are the arguments
     *               provided by the AI model (often parsed from a JSON string).
     * @return The current {@code Tool} instance, allowing for fluent configuration.
     */
    Tool setInputs(Map<String, Object> inputs);

    /**
     * Gets the call ID associated with a specific invocation of this tool.
     * This ID is typically provided by the AI model in its tool call request and is used
     * to correlate the tool's execution result back to that specific request when
     * communicating with the model.
     *
     * @return The call ID string, or null if not set for the current invocation.
     */
    String getCallId();

    /**
     * Sets the call ID for a specific invocation of this tool.
     *
     * @param callId The call ID string from the AI model's tool call request.
     * @return The current {@code Tool} instance, allowing for fluent configuration.
     */
    Tool setCallId(String callId);

    /**
     * Gets the result of the tool's execution.
     * This value is populated after {@link #executeCallable()} has been successfully invoked.
     * The type of the result object is determined by the tool's {@link ToolCallable} logic.
     *
     * @return The result object, or null if the tool has not been executed,
     *         or if the execution resulted in no specific return value (void-like).
     */
    Object getResult();

    /**
     * Executes the tool's registered {@link ToolCallable} logic using the currently set inputs
     * (see {@link #setInputs(Map)}).
     *
     * <p>Before executing the callable, this method should ideally perform validation,
     * such as checking if all required parameters (defined by {@link #getRequiredParameters()})
     * are present in the inputs. {@link BaseTool} provides such an implementation.</p>
     *
     * <p>The result of the execution is stored internally and can be retrieved using {@link #getResult()}.</p>
     *
     * @throws com.skanga.tools.exceptions.MissingToolParameterException if a required parameter is not found in the inputs.
     * @throws com.skanga.tools.exceptions.ToolCallableException if the {@link ToolCallable} has not been set,
     *         or if the callable itself throws an exception during its execution.
     * @throws Exception if any other unexpected error occurs.
     */
    void executeCallable() throws Exception;
}

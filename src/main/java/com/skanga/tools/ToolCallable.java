package com.skanga.tools;

/**
 * A functional interface representing the executable logic of a {@link Tool}.
 * It defines a single method {@link #execute(ToolExecutionInput)} that takes
 * structured input and returns a result.
 *
 * <p>This interface allows tool implementations to be defined concisely using lambda expressions
 * or method references, decoupling the tool's definition (name, description, parameters)
 * from its actual execution logic.</p>
 *
 * <p>Example usage with a lambda:</p>
 * <pre>{@code
 * Tool myTool = new BaseTool("weather_lookup", "Gets the current weather for a city.");
 * myTool.addParameter(new StringToolProperty("city", "The city name.", true));
 * myTool.setCallable(input -> {
 *     String city = (String) input.getArgument("city");
 *     // ... call weather API ...
 *     String weatherData = "Sunny in " + city;
 *     return new ToolExecutionResult(weatherData);
 * });
 * }</pre>
 */
@FunctionalInterface
public interface ToolCallable {
    /**
     * Executes the core logic of the tool using the provided input.
     *
     * @param input The {@link ToolExecutionInput} containing named arguments for the tool.
     *              The tool implementation is responsible for validating and using these arguments.
     * @return A {@link ToolExecutionResult} wrapping the output of the tool's execution.
     *         The result can be any object, or null if the tool produces no specific output.
     * @throws Exception if any error occurs during the tool's execution.
     *                   Implementations are encouraged to throw more specific exceptions
     *                   (e.g., extending {@link com.skanga.tools.exceptions.ToolException})
     *                   to provide better error context.
     */
    ToolExecutionResult execute(ToolExecutionInput input) throws Exception;
}

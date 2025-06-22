package com.skanga.tools;

/**
 * Represents the result of a {@link Tool}'s execution.
 * This record is a simple wrapper around the actual result object produced by the tool.
 *
 * @param result The object returned by the tool's execution logic.
 *               This can be of any type, as tools may produce diverse outputs
 *               (e.g., a {@link String}, a {@link java.util.Map}, a custom domain object, or even null).
 *               It is the responsibility of the component that receives this result
 *               (e.g., an AI model or an agent) to interpret it correctly, often by
 *               serializing it to a string (like JSON) if it needs to be fed back to an LLM.
 */
public record ToolExecutionResult(Object result) {
    // No additional logic or validation needed for this simple data carrier.
    // The `result` can be null if the tool's execution naturally yields no specific output
    // or if it represents a void operation.
}

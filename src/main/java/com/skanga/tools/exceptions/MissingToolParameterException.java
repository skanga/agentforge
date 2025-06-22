package com.skanga.tools.exceptions;

/**
 * Exception thrown when a required parameter for a {@link com.skanga.tools.Tool}
 * is missing during an attempt to execute the tool.
 * This typically occurs if the inputs provided to the tool do not include a value
 * for a parameter that was marked as required in the tool's definition.
 */
public class MissingToolParameterException extends ToolException {

    private final String parameterName;
    private final String toolName;

    /**
     * Constructs a MissingToolParameterException with a specific message, tool name, and parameter name.
     * @param message The detail message.
     * @param toolName The name of the tool for which the parameter is missing.
     * @param parameterName The name of the missing required parameter.
     */
    public MissingToolParameterException(String message, String toolName, String parameterName) {
        super(message);
        this.toolName = toolName;
        this.parameterName = parameterName;
    }

    /**
     * Constructs a MissingToolParameterException with a specific message, cause, tool name, and parameter name.
     * @param message The detail message.
     * @param cause The underlying cause.
     * @param toolName The name of the tool.
     * @param parameterName The name of the missing parameter.
     */
    public MissingToolParameterException(String message, Throwable cause, String toolName, String parameterName) {
        super(message, cause);
        this.toolName = toolName;
        this.parameterName = parameterName;
    }

    /**
     * Constructs a MissingToolParameterException with a default message format.
     * @param toolName The name of the tool for which the parameter is missing.
     * @param parameterName The name of the missing required parameter.
     */
    public MissingToolParameterException(String toolName, String parameterName) {
        super("Required parameter '" + parameterName + "' is missing for tool: " + toolName);
        this.toolName = toolName;
        this.parameterName = parameterName;
    }

    /**
     * Gets the name of the missing parameter.
     * @return The parameter name.
     */
    public String getParameterName() {
        return parameterName;
    }

    /**
     * Gets the name of the tool for which the parameter was missing.
     * @return The tool name.
     */
    public String getToolName() {
        return toolName;
    }
}

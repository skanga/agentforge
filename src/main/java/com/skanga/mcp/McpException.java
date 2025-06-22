package com.skanga.mcp;

/**
 * Custom runtime exception for errors related to the Model Context Protocol (MCP) operations.
 * This can be thrown by {@link McpTransport} implementations or the {@link McpClient}
 * for issues such as connection failures, errors sending or receiving data,
 * JSON-RPC error responses from the MCP server, or process management problems.
 */
public class McpException extends RuntimeException {

    /**
     * Constructs a new MCP exception with the specified detail message.
     * @param message the detail message.
     */
    public McpException(String message) {
        super(message);
    }

    /**
     * Constructs a new MCP exception with the specified detail message and cause.
     * @param message the detail message.
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
     */
    public McpException(String message, Throwable cause) {
        super(message, cause);
    }
}

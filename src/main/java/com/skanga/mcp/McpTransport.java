package com.skanga.mcp;

import java.util.Map;

/**
 * Interface for Model Context Protocol (MCP) transport layers.
 * Defines the contract for how messages (typically JSON-RPC requests and responses
 * represented as {@code Map<String, Object>}) are sent to and received from an
 * MCP server process or endpoint.
 *
 * <p>Implementations will handle the specifics of the communication mechanism,
 * such as standard I/O ({@link StdioMcpTransport}), WebSockets, or other IPC methods.</p>
 */
public interface McpTransport {

    /**
     * Establishes a connection to the MCP server or starts the server process.
     * For {@link StdioMcpTransport}, this typically involves launching the external command.
     * This method should be called before any send/receive operations.
     *
     * @throws McpException if the connection cannot be established or the server process
     *                      cannot be started.
     */
    void connect() throws McpException;

    /**
     * Sends data, typically a JSON-RPC request map, to the MCP server.
     * The data is usually serialized to a JSON string before transmission.
     *
     * @param data The map representing the JSON data to send.
     * @throws McpException if sending the data fails (e.g., I/O error, serialization error,
     *                      or if the transport is not connected).
     */
    void send(Map<String, Object> data) throws McpException;

    /**
     * Receives data, typically a JSON-RPC response map, from the MCP server.
     * This method may block until a complete message is received or a timeout occurs,
     * depending on the implementation. The received data is usually a JSON string
     * that is deserialized into a map.
     *
     * @return A map representing the JSON data received from the server.
     * @throws McpException if receiving data fails (e.g., I/O error, deserialization error,
     *                      timeout, or if the transport is not connected).
     */
    Map<String, Object> receive() throws McpException;

    /**
     * Disconnects from the MCP server and cleans up resources.
     * For {@link StdioMcpTransport}, this involves terminating the server process
     * and closing associated I/O streams.
     *
     * @throws McpException if disconnection fails or errors occur during resource cleanup.
     */
    void disconnect() throws McpException;

    /**
     * Checks if the transport layer is currently connected and active.
     * For process-based transports, this should also check if the underlying process is alive.
     *
     * @return {@code true} if connected and active, {@code false} otherwise.
     */
    boolean isConnected();
}

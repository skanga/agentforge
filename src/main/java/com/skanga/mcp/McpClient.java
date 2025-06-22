package com.skanga.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
// Tool property classes are not directly used by McpClient, but by McpConnector
// import com.skanga.tools.properties.ArrayToolProperty;
// import com.skanga.tools.properties.BaseToolProperty;
// import com.skanga.tools.properties.ObjectToolProperty;
// import com.skanga.tools.properties.PropertyType;
// import com.skanga.tools.properties.ToolProperty;
// import com.skanga.tools.properties.ToolPropertySchema;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A client for interacting with a Model Context Protocol (MCP) server.
 * MCP is typically a JSON-RPC based protocol. This client handles the construction
 * of JSON-RPC requests, sending them via an {@link McpTransport}, and processing
 * the responses.
 *
 * <p><b>Key Operations:</b>
 * <ul>
 *   <li>Initialization: Sends "initialize" and "notifications/initialized" messages
 *       to the MCP server upon connection.</li>
 *   <li>Tool Listing: Provides {@link #listTools()} to fetch available tools from the server,
 *       handling pagination if supported by the server.</li>
 *   <li>Tool Calling: Provides {@link #callTool(String, Map)} to execute a specific tool
 *       on the server with given arguments.</li>
 * </ul>
 * </p>
 *
 * <p>It uses an {@link McpTransport} (by default, {@link StdioMcpTransport}) for the
 * actual communication with the MCP server process/endpoint.</p>
 *
 * <p><b>JSON-RPC Structure:</b>
 * This client constructs JSON-RPC 2.0 compliant messages:
 * <ul>
 *   <li>Requests include `jsonrpc: "2.0"`, `id` (auto-incrementing integer as string),
 *       `method` (e.g., "tools/list"), and optional `params`.</li>
 *   <li>Notifications (like "notifications/initialized") include `jsonrpc` and `method`, but no `id`.</li>
 *   <li>Responses are expected to contain either a `result` field or an `error` field.
 *       The `error` field is a map with `code` and `message`.</li>
 * </ul>
 * </p>
 */
public class McpClient {

    private final McpTransport transport;
    private final AtomicInteger requestId = new AtomicInteger(0); // For generating unique JSON-RPC request IDs
    private final ObjectMapper objectMapper = new ObjectMapper(); // For serializing error data if needed

    /**
     * Constructs an McpClient with a configuration map for the MCP server.
     * This typically initializes an {@link StdioMcpTransport}.
     *
     * @param config A map containing configuration for the MCP server. Expected keys:
     *               <ul>
     *                 <li>"command" (String, required): The command to start the MCP server.</li>
     *                 <li>"args" (List&lt;String&gt;, optional): Arguments for the command.</li>
     *                 <li>"env" (Map&lt;String, String&gt;, optional): Environment variables for the server process.</li>
     *               </ul>
     * @throws McpException if the transport cannot be connected or initialization fails.
     * @throws IllegalArgumentException if required configuration (like "command") is missing.
     */
    public McpClient(Map<String, Object> config) throws McpException {
        Objects.requireNonNull(config, "MCP client configuration cannot be null.");
        String command = (String) config.get("command");
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("MCP server configuration must contain a 'command' string.");
        }
        @SuppressWarnings("unchecked") // Caller's responsibility to ensure correct types in config map
        List<String> args = (List<String>) config.get("args");
        @SuppressWarnings("unchecked")
        Map<String, String> env = (Map<String, String>) config.get("env");

        this.transport = new StdioMcpTransport(command, args, env);
        // Connect and initialize immediately upon client creation.
        this.transport.connect();
        initialize();
    }

    /**
     * Constructs an McpClient with a pre-configured {@link McpTransport}.
     * If the transport is not already connected, this constructor will attempt to connect it.
     * Then, it performs the MCP initialization handshake.
     *
     * @param transport The McpTransport instance to use. Must not be null.
     * @throws McpException if the transport is not connected and connection fails, or if initialization fails.
     */
    public McpClient(McpTransport transport) throws McpException {
        this.transport = Objects.requireNonNull(transport, "McpTransport cannot be null.");
        if (!this.transport.isConnected()) {
            this.transport.connect();
        }
        initialize();
    }


    /**
     * Performs the MCP initialization handshake.
     * Sends an "initialize" request and a "notifications/initialized" notification.
     * @throws McpException if sending or receiving fails, or if the initialize response indicates an error.
     */
    private void initialize() throws McpException {
        // 1. Send "initialize" request
        Map<String, Object> clientCapabilities = new HashMap<>(); // Placeholder for actual capabilities
        // clientCapabilities.put("yourClientCapability", true);

        Map<String, Object> initializeParams = Map.of(
            "protocolVersion", "1.0.0", // Example protocol version
            "clientName", "jmcp-java-sdk",
            "clientVersion", "0.1.0", // Example SDK/client version
            "capabilities", clientCapabilities
        );
        Map<String, Object> initResponse = sendRequestAndGetResponse("initialize", initializeParams);

        // Optional: Validate initResponse.result for server capabilities or confirmation.
        // For example, if result contains {"serverName": "...", "serverVersion": "..."}
        // System.out.println("MCP Server initialized: " + initResponse.get("result"));

        // 2. Send "notifications/initialized" notification (no ID, no response expected from server for this)
        Map<String, Object> initializedNotification = createJsonRpcNotification("notifications/initialized", null);
        transport.send(initializedNotification);
        // Per JSON-RPC, no response for notifications. PHP client also doesn't wait for one here.
    }

    /**
     * Lists available tools from the MCP server.
     * Handles pagination if the server uses `nextCursor`.
     *
     * @return A list of tool definitions, where each tool definition is a {@code Map<String, Object>}.
     * @throws McpException if the API call fails or the response is malformed.
     */
    public List<Map<String, Object>> listTools() throws McpException {
        List<Map<String, Object>> allTools = new ArrayList<>();
        String cursor = null;
        Map<String, Object> params;

        do {
            params = new HashMap<>();
            if (cursor != null && !cursor.isEmpty()) {
                params.put("cursor", cursor);
            }
            // Some servers might support a "limit" parameter, e.g., params.put("limit", 50);
            // The PHP client did not seem to send a limit by default for listTools.

            Map<String, Object> rpcResponse = sendRequestAndGetResponse("tools/list", params);

            Object resultObj = rpcResponse.get("result");
            if (!(resultObj instanceof Map)) {
                throw new McpException("MCP response for 'tools/list' has invalid 'result' field type. Expected Map, got: " + (resultObj != null ? resultObj.getClass().getName() : "null"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) resultObj;

            Object toolsBatchObj = result.get("tools");
            if (toolsBatchObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> toolsBatch = (List<Map<String, Object>>) toolsBatchObj;
                allTools.addAll(toolsBatch);
            } else if (toolsBatchObj != null) {
                 throw new McpException("MCP 'tools/list' response 'tools' field is not a list. Got: " + toolsBatchObj.getClass().getName());
            }
            // else, toolsBatchObj is null, meaning no tools in this page or an empty list was fine.

            cursor = (String) result.get("nextCursor"); // If null or empty, pagination stops.

        } while (cursor != null && !cursor.isEmpty());

        return allTools;
    }

    /**
     * Calls a specific tool on the MCP server.
     *
     * @param toolName  The name of the tool to call. Must not be null.
     * @param arguments A map of arguments to pass to the tool. Can be null or empty.
     * @return The "result" part of the JSON-RPC response from the MCP server, which represents
     *         the tool's execution output (typically a {@code Map<String, Object>}).
     * @throws McpException if the tool name is null, the API call fails, or the response is malformed.
     */
    public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) throws McpException {
        Objects.requireNonNull(toolName, "Tool name for callTool cannot be null.");
        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments == null ? Collections.emptyMap() : arguments);

        Map<String, Object> rpcResponse = sendRequestAndGetResponse("tools/call", params);

        Object resultObj = rpcResponse.get("result");
        if (!(resultObj instanceof Map)) {
             throw new McpException("MCP response for 'tools/call' (" + toolName + ") has invalid 'result' field type. Expected Map, got: " + (resultObj != null ? resultObj.getClass().getName() : "null"));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resultObj;
        return result;
    }

    /**
     * Disconnects the underlying transport from the MCP server.
     * @throws McpException if disconnection fails.
     */
    public void disconnect() throws McpException {
        if (transport != null && transport.isConnected()) {
            // Optional: Send a "shutdown" or "exit" notification if the MCP spec includes one.
            // E.g.: Map<String, Object> shutdownNotification = createJsonRpcNotification("session/shutdown", null);
            //       transport.send(shutdownNotification);
            // This depends on the specific MCP server implementation.
            transport.disconnect();
        }
    }

    /**
     * Creates a JSON-RPC 2.0 request map.
     * @param method The method name.
     * @param params The parameters map (can be null).
     * @return The JSON-RPC request as a Map.
     */
    private Map<String, Object> createJsonRpcRequest(String method, Map<String, Object> params) {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", String.valueOf(requestId.incrementAndGet())); // ID as string, per some JSON-RPC practices
        request.put("method", method);
        if (params != null && !params.isEmpty()) {
            request.put("params", params);
        }
        return request;
    }

    /**
     * Creates a JSON-RPC 2.0 notification map.
     * @param method The method name.
     * @param params The parameters map (can be null).
     * @return The JSON-RPC notification as a Map.
     */
    private Map<String, Object> createJsonRpcNotification(String method, Map<String, Object> params) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method); // No "id" field for notifications
        if (params != null && !params.isEmpty()) {
            notification.put("params", params);
        }
        return notification;
    }

    /**
     * Sends a JSON-RPC request and processes the response, checking for JSON-RPC level errors.
     * @param method The JSON-RPC method name.
     * @param params The parameters for the method.
     * @return The full JSON-RPC response map if successful.
     * @throws McpException if the transport fails, or if the response contains a JSON-RPC error object.
     */
    private Map<String, Object> sendRequestAndGetResponse(String method, Map<String, Object> params) throws McpException {
        Map<String, Object> request = createJsonRpcRequest(method, params);
        transport.send(request);
        Map<String, Object> response = transport.receive(); // This should be the full JSON-RPC response

        // Check for JSON-RPC level error
        if (response.containsKey("error") && response.get("error") != null) {
            Object errorField = response.get("error");
            if (!(errorField instanceof Map)) {
                 throw new McpException("MCP response 'error' field is malformed. Expected a Map. Got: " + errorField.getClass().getName() + ". Full response: " + response);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> errorObj = (Map<String, Object>) errorField;

            String errorMessage = "MCP server returned an error for method '" + method + "': " +
                                  errorObj.getOrDefault("message", "Unknown error") +
                                  " (Code: " + errorObj.getOrDefault("code", "N/A") + ")";

            if (errorObj.containsKey("data")) {
                try {
                    // Attempt to serialize 'data' part of error for more context
                    errorMessage += " Data: " + objectMapper.writeValueAsString(errorObj.get("data"));
                } catch (JsonProcessingException e) {
                    // If data serialization fails, append its toString or class name
                    errorMessage += " Data: [Unserializable: " + errorObj.get("data").toString() + "]";
                }
            }
            throw new McpException(errorMessage);
        }

        // It's generally expected that a JSON-RPC response to a request (which has an ID)
        // will contain a "result" field, even if its value is null.
        // If "result" is missing entirely, it might indicate a spec deviation or an issue.
        // However, some servers might omit "result" if it's truly null.
        // For robustness, we ensure "result" key exists if no error.
        if (!response.containsKey("result") && !response.containsKey("error")) {
             // This could be an issue or valid if result is optional and null.
             // For now, let's assume result should be there if no error.
             // Some MCP methods might not have a result (like a notification, but we send requests here).
             // If a method is defined to return void/no result, the server might send "result": null.
             System.err.println("Warning: MCP response for method '" + method + "' is missing 'result' field, but no 'error' field was present. Response: " + response);
             // To avoid breaking if result is optional and omitted when null:
             // response.putIfAbsent("result", null);
        }
        return response;
    }
}

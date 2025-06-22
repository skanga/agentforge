package com.skanga.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * A merged and consolidated test suite for the {@link McpClient} class.
 * This class combines tests from McpClientTests and McpClientTests2,
 * removing redundancy and standardizing on best practices.
 */
@ExtendWith(MockitoExtension.class)
class McpClientTest {

    @Mock
    private McpTransport transport;

    private McpClient client;

    /**
     * Helper to create a standard JSON-RPC success response.
     */
    private Map<String, Object> createSuccessRpcResponse(Object id, Object result) {
        return Map.of("jsonrpc", "2.0", "id", id, "result", result);
    }

    /**
     * Helper to create a standard JSON-RPC error response.
     */
    private Map<String, Object> createErrorRpcResponse(Object id, int code, String message) {
        return Map.of("jsonrpc", "2.0", "id", id, "error", Map.of("code", code, "message", message));
    }

    /**
     * Sets up a fully initialized client for use in most tests.
     * This handles the 'initialize' call that occurs within the McpClient constructor.
     */
    @BeforeEach
    void setUp() {
        when(transport.isConnected()).thenReturn(true);

        // Mock the response to the 'initialize' request sent by the constructor
        Map<String, Object> initResult = Map.of("serverCapabilities", Collections.emptyMap());
        when(transport.receive()).thenReturn(Map.of("result", initResult));

        client = new McpClient(transport);
    }

    // --- Constructor Tests ---

    @Test
    void constructor_withNullTransport_shouldThrowException() {
        assertThrows(NullPointerException.class, () -> new McpClient((McpTransport) null));
    }

    @Test
    void constructor_withConfig_shouldCreateInstance() {
        Map<String, Object> config = Map.of(
                "command", "test-command",
                "args", Arrays.asList("arg1", "arg2"),
                "env", Map.of("TEST_VAR", "test_value")
        );
        // This test assumes that if no exception is thrown, the internal StdioMcpTransport was created successfully.
        assertDoesNotThrow(() -> new McpClient(config));
    }

    @Test
    void constructor_withInvalidConfig_shouldThrowException() {
        Map<String, Object> config = Map.of("invalid", "config");
        assertThrows(IllegalArgumentException.class, () -> new McpClient(config));
    }

    @Test
    void constructor_withNullCommandInConfig_shouldThrowException() {
        Map<String, Object> config = Map.of("command", null);
        assertThrows(IllegalArgumentException.class, () -> new McpClient(config));
    }

    // --- listTools Tests ---

    @Test
    void listTools_withSinglePage_shouldReturnTools() throws McpException {
        // Arrange
        Map<String, Object> toolDef = Map.of(
                "name", "test_tool",
                "description", "A test tool",
                "inputSchema", Map.of("type", "object")
        );
        Map<String, Object> listResult = Map.of("tools", List.of(toolDef));
        // Override the setUp mock with a specific one for this test
        when(transport.receive()).thenReturn(createSuccessRpcResponse("list-id-1", listResult));

        // Act
        List<Map<String, Object>> tools = client.listTools();

        // Assert
        assertThat(tools).hasSize(1);
        Map<String, Object> tool = tools.get(0);
        assertThat(tool.get("name")).isEqualTo("test_tool");
        assertThat(tool.get("description")).isEqualTo("A test tool");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(transport).send(captor.capture());
        assertThat(captor.getValue().get("method")).isEqualTo("tools/list");
    }

    @Test
    void listTools_withMultiplePages_shouldHandlePagination() throws McpException {
        // Arrange
        Map<String, Object> toolA = Map.of("name", "toolA");
        Map<String, Object> toolB = Map.of("name", "toolB");

        Map<String, Object> page1Result = Map.of("tools", List.of(toolA), "nextCursor", "cursor123");
        Map<String, Object> page2Result = Map.of("tools", List.of(toolB)); // No more cursor

        // Set up sequential responses for the two `receive` calls
        when(transport.receive())
                .thenReturn(createSuccessRpcResponse(2, page1Result))
                .thenReturn(createSuccessRpcResponse(3, page2Result));

        // Act
        List<Map<String, Object>> tools = client.listTools();

        // Assert
        assertThat(tools).hasSize(2);
        assertThat(tools).extracting(tool -> tool.get("name")).containsExactly("toolA", "toolB");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(transport, times(2)).send(captor.capture());

        List<Map<String, Object>> sentRequests = captor.getAllValues();
        Map<String, Object> request1 = sentRequests.get(0);
        assertThat(request1.get("method")).isEqualTo("tools/list");
        assertThat(request1.get("params")).isNull(); // First call has no cursor

        Map<String, Object> request2 = sentRequests.get(1);
        assertThat(request2.get("method")).isEqualTo("tools/list");
        Map<String, Object> paramsPage2 = (Map<String, Object>) request2.get("params");
        assertThat(paramsPage2.get("cursor")).isEqualTo("cursor123");
    }

    @Test
    void listTools_whenServerReturnsError_shouldThrowMcpException() {
        // Arrange
        when(transport.receive()).thenReturn(createErrorRpcResponse(2, -32000, "Server error listing tools"));

        // Act & Assert
        McpException ex = assertThrows(McpException.class, () -> client.listTools());
        assertThat(ex.getMessage()).contains("Server error listing tools").contains("Code: -32000");
    }

    // --- callTool Tests ---

    @Test
    void callTool_withValidArguments_shouldReturnResult() throws McpException {
        // Arrange
        String toolName = "calculator.add";
        Map<String, Object> arguments = Map.of("a", 5, "b", 7);
        Map<String, Object> toolExecutionResult = Map.of("content", List.of(Map.of("type", "text", "text", "Result: 12")));

        when(transport.receive()).thenReturn(createSuccessRpcResponse(2, toolExecutionResult));

        // Act
        Map<String, Object> result = client.callTool(toolName, arguments);

        // Assert
        assertThat(result).isEqualTo(toolExecutionResult);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(transport).send(captor.capture());
        Map<String, Object> sentRequest = captor.getValue();
        assertThat(sentRequest.get("method")).isEqualTo("tools/call");

        Map<String, Object> params = (Map<String, Object>) sentRequest.get("params");
        assertThat(params.get("name")).isEqualTo(toolName);
        assertThat(params.get("arguments")).isEqualTo(arguments);
    }

    @Test
    void callTool_withNullToolName_shouldThrowException() {
        assertThrows(NullPointerException.class, () -> client.callTool(null, Map.of()));
    }

    @Test
    void callTool_withNullArguments_shouldUseEmptyMap() throws Exception {
        // Arrange
        when(transport.receive()).thenReturn(createSuccessRpcResponse(2, Map.of("content", Collections.emptyList())));

        // Act
        client.callTool("test_tool", null);

        // Assert
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(transport).send(captor.capture());
        Map<String, Object> sentRequest = captor.getValue();
        Map<String, Object> params = (Map<String, Object>) sentRequest.get("params");
        Map<String, Object> args = (Map<String, Object>) params.get("arguments");

        assertThat(args).isNotNull().isEmpty();
    }

    @Test
    void callTool_whenServerReturnsError_shouldThrowMcpException() {
        // Arrange
        when(transport.receive()).thenReturn(createErrorRpcResponse(2, -32001, "Tool execution failed"));

        // Act & Assert
        McpException ex = assertThrows(McpException.class, () -> client.callTool("failingTool", Collections.emptyMap()));
        assertThat(ex.getMessage()).contains("Tool execution failed");
    }

    @Test
    void callTool_whenResponseIsMalformed_shouldThrowMcpException() {
        // Arrange - Malformed response with no 'result' or 'error' key
        Map<String, Object> malformedRpcResponse = Map.of("jsonrpc", "2.0", "id", 2);
        when(transport.receive()).thenReturn(malformedRpcResponse);

        // Act & Assert
        McpException ex = assertThrows(McpException.class, () -> client.callTool("testTool", null));
        assertThat(ex.getMessage()).contains("has invalid 'result' field type");
    }

    // --- Disconnect Tests ---

    @Test
    void disconnect_whenConnected_shouldCallTransportDisconnect() throws Exception {
        // Act
        client.disconnect();

        // Assert
        verify(transport).disconnect();
    }

    @Test
    void disconnect_whenNotConnected_shouldNotCallTransportAndNotThrow() throws Exception {
        // Arrange
        when(transport.isConnected()).thenReturn(false);

        // Act & Assert
        assertDoesNotThrow(() -> client.disconnect());
        verify(transport, never()).disconnect();
    }
}
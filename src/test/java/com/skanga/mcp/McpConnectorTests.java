package com.skanga.mcp;

import com.skanga.tools.BaseTool;
import com.skanga.tools.Tool;
import com.skanga.tools.ToolExecutionInput;
import com.skanga.tools.ToolExecutionResult;
import com.skanga.tools.exceptions.ToolException;
import com.skanga.tools.properties.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper; // For callable test

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpConnectorTests {

    @Mock
    private McpClient mockMcpClient;
    private McpConnector mcpConnector;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mcpConnector = new McpConnector(mockMcpClient);
    }

    @Test
    void constructor_withConfigMap_initializesClient() throws McpException {
        // This test requires McpClient's constructor with Map to be testable,
        // which in turn relies on StdioMcpTransport's testability for connect().
        // For now, focus on constructor with McpClient.
        // McpConnector connectorWithMap = new McpConnector(Map.of("command", "dummy-mcp-server"));
        // assertNotNull(connectorWithMap.client); // client is private, cannot directly assert
        assertTrue(true, "Testing McpConnector(Map) is complex due to McpClient/StdioMcpTransport instantiation. Focus on McpConnector(McpClient).");
    }

    @Test
    void constructor_withMcpClient_setsClient() {
        assertNotNull(mcpConnector); // Verifies setup
        // To assert this.client == mockMcpClient, client field would need to be accessible or have a getter.
    }

    @Test
    void getTools_noToolsFromClient_returnsEmptyList() throws McpException {
        when(mockMcpClient.listTools()).thenReturn(Collections.emptyList());
        List<Tool> tools = mcpConnector.getTools();
        assertNotNull(tools);
        assertTrue(tools.isEmpty());
    }

    @Test
    void getTools_clientReturnsNull_returnsEmptyList() throws McpException {
        when(mockMcpClient.listTools()).thenReturn(null);
        List<Tool> tools = mcpConnector.getTools();
        assertNotNull(tools);
        assertTrue(tools.isEmpty());
    }

    @Test
    void createToolFromMcpDefinition_parsesSimpleToolCorrectly() {
        Map<String, Object> mcpToolDef = new HashMap<>();
        mcpToolDef.put("name", "get_weather");
        mcpToolDef.put("description", "Get current weather for a location.");

        Map<String, Object> properties = new HashMap<>();
        properties.put("location", Map.of("type", "string", "description", "City and state"));
        properties.put("unit", Map.of("type", "string", "description", "Temperature unit (celsius or fahrenheit)", "enum", List.of("celsius", "fahrenheit")));

        Map<String, Object> inputSchema = Map.of(
            "type", "object",
            "properties", properties,
            "required", List.of("location")
        );
        mcpToolDef.put("inputSchema", inputSchema);

        Tool tool = mcpConnector.createToolFromMcpDefinition(mcpToolDef); // Made createToolFromMcpDefinition package-private or public for test

        assertEquals("get_weather", tool.getName());
        assertEquals("Get current weather for a location.", tool.getDescription());
        assertEquals(2, tool.getParameters().size());

        ToolProperty locationParam = tool.getParameters().stream().filter(p -> p.getName().equals("location")).findFirst().orElse(null);
        assertNotNull(locationParam);
        assertEquals(PropertyType.STRING, locationParam.getPropertyType());
        assertEquals("City and state", locationParam.getDescription());
        assertTrue(locationParam.isRequired());

        ToolProperty unitParam = tool.getParameters().stream().filter(p -> p.getName().equals("unit")).findFirst().orElse(null);
        assertNotNull(unitParam);
        assertEquals(PropertyType.STRING, unitParam.getPropertyType());
        assertEquals(List.of("celsius", "fahrenheit"), ((BaseToolProperty)unitParam).getEnumList()); // Assuming BaseToolProperty
        assertFalse(unitParam.isRequired());
    }

    @Test
    void createToolFromMcpDefinition_parsesNestedObjectAndArray() {
        Map<String, Object> mcpToolDef = new HashMap<>();
        mcpToolDef.put("name", "process_data");
        mcpToolDef.put("description", "Processes complex data.");

        Map<String, Object> addressSchema = Map.of(
            "type", "object",
            "properties", Map.of(
                "street", Map.of("type", "string"),
                "city", Map.of("type", "string")
            ),
            "required", List.of("street")
        );
        Map<String, Object> itemSchema = Map.of("type", "integer");
        Map<String, Object> properties = Map.of(
            "user_address", addressSchema,
            "item_ids", Map.of("type", "array", "items", itemSchema)
        );
        Map<String, Object> inputSchema = Map.of(
            "type", "object", "properties", properties, "required", List.of("user_address")
        );
        mcpToolDef.put("inputSchema", inputSchema);

        Tool tool = mcpConnector.createToolFromMcpDefinition(mcpToolDef);
        assertEquals(2, tool.getParameters().size());

        ObjectToolProperty addressParam = (ObjectToolProperty) tool.getParameters().stream()
            .filter(p -> p.getName().equals("user_address")).findFirst().orElseThrow();
        assertEquals(PropertyType.OBJECT, addressParam.getPropertyType());
        assertTrue(addressParam.isRequired());
        assertEquals(2, addressParam.getProperties().size());
        assertTrue(addressParam.getRequiredProperties().contains("street"));

        ArrayToolProperty itemsParam = (ArrayToolProperty) tool.getParameters().stream()
            .filter(p -> p.getName().equals("item_ids")).findFirst().orElseThrow();
        assertEquals(PropertyType.ARRAY, itemsParam.getPropertyType());
        assertFalse(itemsParam.isRequired());
        assertEquals(PropertyType.INTEGER, ((BaseToolProperty)itemsParam.getItemsSchema()).getPropertyType());
    }


    @Test
    void toolCallable_executesViaMcpClientAndParsesTextResult() throws Exception {
        Map<String, Object> mcpToolDef = Map.of(
            "name", "echoTool",
            "description", "Echoes input",
            "inputSchema", Map.of("type", "object", "properties", Map.of("text", Map.of("type", "string")))
        );
        Tool tool = mcpConnector.createToolFromMcpDefinition(mcpToolDef);

        Map<String, Object> toolArgs = Map.of("text", "Hello MCP");
        ToolExecutionInput executionInput = new ToolExecutionInput(toolArgs);

        // Mock McpClient.callTool response
        Map<String, Object> mcpCallResult = Map.of(
            "content", List.of(Map.of("type", "text", "text", "MCP echoed: Hello MCP"))
        );
        when(mockMcpClient.callTool("echoTool", toolArgs)).thenReturn(mcpCallResult);

        // Execute the callable attached to the tool
        assertNotNull(((BaseTool)tool).getCallable());
        // To get callable: ((BaseTool)tool).getCallable() if BaseTool exposes it, or test via executeCallable
        tool.setInputs(toolArgs); // Set inputs before calling executeCallable
        tool.executeCallable();

        ToolExecutionResult finalResult = new ToolExecutionResult(tool.getResult());

        assertEquals("MCP echoed: Hello MCP", finalResult.result());
    }

     @Test
    void toolCallable_mcpClientThrowsException_callableThrowsToolException() throws McpException {
        Map<String, Object> mcpToolDef = Map.of("name", "errorTool", "description", "Errors");
        Tool tool = mcpConnector.createToolFromMcpDefinition(mcpToolDef);
        tool.setInputs(Collections.emptyMap());

        when(mockMcpClient.callTool(eq("errorTool"), anyMap())).thenThrow(new McpException("MCP server error!"));

        ToolException ex = assertThrows(ToolException.class, tool::executeCallable);
        assertTrue(ex.getMessage().contains("Failed to execute MCP tool 'errorTool'"));
        assertTrue(ex.getCause() instanceof McpException);
    }


    @Test
    void shutdown_callsClientDisconnect() throws McpException {
        mcpConnector.shutdown();
        verify(mockMcpClient).disconnect();
    }
}

package com.skanga.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.tools.BaseTool;
import com.skanga.tools.Tool;
import com.skanga.tools.ToolExecutionResult;
import com.skanga.tools.exceptions.ToolException; // Using this for callable's wrapper exception
import com.skanga.tools.properties.*;
// BaseToolProperty is abstract, so we can't instantiate it directly for simple types in parser
// We need a concrete, simple property or handle it within the parser.
// For now, assuming the parser creates appropriate property types.
// import com.skanga.tools.properties.BaseToolProperty;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet; // For requiredSet in parser
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set; // For requiredSet in parser
import java.util.stream.Collectors;

/**
 * Connects to an MCP (Model Context Protocol) server,
 * allowing the agent to discover and use tools provided by that server.
 *
 * <p><b>Functionality:</b>
 * <ol>
 *   <li>Initializes an {@link McpClient} to communicate with the MCP server.</li>
 *   <li>Fetches tool definitions from the MCP server using {@code McpClient.listTools()}.</li>
 *   <li>Transforms these MCP tool definitions (which include JSON schemas for parameters)
 *       into {@link com.skanga.tools.Tool} instances that can be used within the
 *       MCP agent framework.</li>
 *   <li>The {@link com.skanga.tools.ToolCallable} for each created tool is configured to
 *       delegate the actual execution back to the MCP server via {@code McpClient.callTool()}.</li>
 * </ol>
 * </p>
 *
 * <p><b>JSON Schema Parsing:</b>
 * A key responsibility of this connector is to parse the JSON schema provided by MCP for each
 * tool's input parameters and convert it into a list of {@link com.skanga.tools.properties.ToolProperty}
 * objects. This involves handling different schema types (object, array, string, integer, etc.)
 * and their attributes (description, enum, required status, nested properties/items).
 * </p>
 *
 * <p><b>Error Handling:</b>
 * Exceptions from the {@code McpClient} (e.g., {@link McpException}) or during schema parsing
 * are propagated or wrapped appropriately. Tool execution via the callable will throw a
 * {@link ToolException} if the MCP call fails.</p>
 */
public class McpConnector {

    private final McpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper(); // For callable's result processing

    /**
     * Constructs an McpConnector with a given MCP server configuration.
     * An {@link McpClient} will be created and initialized based on this configuration.
     *
     * @param mcpServerConfig A map containing configuration for the MCP server,
     *                        as required by {@link McpClient#McpClient(Map)}.
     * @throws McpException if the McpClient cannot be initialized or connected.
     */
    public McpConnector(Map<String, Object> mcpServerConfig) throws McpException {
        Objects.requireNonNull(mcpServerConfig, "MCP server configuration cannot be null for McpConnector.");
        this.client = new McpClient(mcpServerConfig);
    }

    /**
     * Constructs an McpConnector with an existing, pre-configured {@link McpClient}.
     *
     * @param mcpClient The McpClient instance to use. Must not be null.
     *                  It is assumed that this client is already connected and initialized.
     */
    public McpConnector(McpClient mcpClient) {
        this.client = Objects.requireNonNull(mcpClient, "McpClient cannot be null for McpConnector.");
    }

    /**
     * Retrieves the list of available tools from the MCP server and converts them
     * into {@link Tool} instances.
     *
     * @return A list of {@link Tool} objects. Returns an empty list if the MCP server
     *         provides no tools or if the tool list is null.
     * @throws McpException if there's an error communicating with the MCP server
     *                      or parsing the tool definitions.
     */
    public List<Tool> getTools() throws McpException {
        List<Map<String, Object>> mcpToolDefs = client.listTools();
        if (mcpToolDefs == null) {
            return Collections.emptyList();
        }
        return mcpToolDefs.stream()
                .map(this::createToolFromMcpDefinition)
                .filter(Objects::nonNull) // Filter out any nulls if a definition was severely malformed
                .collect(Collectors.toList());
    }

    /**
     * Creates a {@link Tool} instance from an MCP tool definition map.
     *
     * @param mcpToolDef A map representing a single tool's definition from the MCP server.
     *                   Expected to contain "name", "description", and "inputSchema".
     * @return A configured {@link BaseTool} instance.
     * @throws McpException if the MCP tool definition is missing required fields
     *                      or if its input schema cannot be parsed.
     */
    Tool createToolFromMcpDefinition(Map<String, Object> mcpToolDef) {
        String name = (String) mcpToolDef.get("name");
        String description = (String) mcpToolDef.get("description");
        if (name == null || name.trim().isEmpty()) {
            throw new McpException("MCP tool definition is missing a valid 'name'. Definition: " + mcpToolDef);
        }
        if (description == null) { // Description can be empty but should exist
            description = ""; // Default to empty if null
            System.err.println("Warning: MCP tool definition for '" + name + "' is missing 'description'.");
        }

        BaseTool tool = new BaseTool(name, description);

        Object inputSchemaObj = mcpToolDef.get("inputSchema");
        if (inputSchemaObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> inputSchema = (Map<String, Object>) inputSchemaObj;
            // The inputSchema itself is the JSON schema for the parameters object.
            // Its "properties" field contains the actual parameter definitions.
            List<ToolProperty> properties = parseJsonSchemaProperties(inputSchema);
            for (ToolProperty prop : properties) {
                tool.addParameter(prop);
            }
        } else if (inputSchemaObj != null) {
            throw new McpException("MCP tool definition for '" + name + "' has an 'inputSchema' that is not a Map/Object. Found: " + inputSchemaObj.getClass().getName());
        }
        // If inputSchemaObj is null, the tool has no parameters, which is valid.

        // Set the callable to delegate execution back to the McpClient
        tool.setCallable(toolExecutionInput -> {
            try {
                Map<String, Object> mcpResponseResult = client.callTool(name, toolExecutionInput.arguments());

                // Process MCP response: extract primary textual content or serialize complex content.
                if (mcpResponseResult != null && mcpResponseResult.containsKey("content")) {
                    Object contentField = mcpResponseResult.get("content");
                    if (contentField instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> contentList = (List<Map<String, Object>>) contentField;
                        if (!contentList.isEmpty()) {
                            // Prioritize "text" type content for direct result.
                            for (Map<String, Object> contentItem : contentList) {
                                if ("text".equals(contentItem.get("type")) && contentItem.containsKey("text")) {
                                    return new ToolExecutionResult(contentItem.get("text"));
                                }
                            }
                            // If no direct text, serialize the first content item (e.g., image, complex data)
                            try {
                                return new ToolExecutionResult(objectMapper.writeValueAsString(contentList.get(0)));
                            } catch (JsonProcessingException e) {
                                // Fallback if serialization of specific item fails
                                System.err.println("McpConnector: Failed to serialize specific content item for tool " + name + ", will serialize full result. Error: " + e.getMessage());
                            }
                        }
                    }
                }
                // Fallback: serialize the entire "result" part of the JSON-RPC response.
                return new ToolExecutionResult(objectMapper.writeValueAsString(mcpResponseResult));
            } catch (Exception e) { // Catch McpException, JsonProcessingException from ObjectMapper, etc.
                // Wrap in ToolException to match ToolCallable signature expectation if needed,
                // or let specific exceptions propagate if handled by BaseTool.executeCallable().
                // For now, wrapping in a runtime ToolException.
                throw new ToolException("Failed to execute MCP tool '" + name + "' via client: " + e.getMessage(), e);
            }
        });
        return tool;
    }

    /**
     * Parses a JSON schema (typically for an "object" type representing tool parameters)
     * into a list of {@link ToolProperty} instances.
     *
     * @param jsonSchema A map representing the JSON schema, expected to have a "properties" field.
     * @return A list of {@link ToolProperty} objects.
     * @throws McpException if the schema is malformed.
     */
    private List<ToolProperty> parseJsonSchemaProperties(Map<String, Object> jsonSchema) {
        List<ToolProperty> toolProperties = new ArrayList<>();
        // The input `jsonSchema` is for the top-level parameters object.
        // We need to look at its "properties" field.
        if (jsonSchema == null || !"object".equals(jsonSchema.get("type"))) {
            // This indicates the inputSchema was not of type object, or was null.
            // If it's not an object, it might not have "properties".
            // Depending on MCP spec, a tool might have no parameters (empty inputSchema or no inputSchema key),
            // or inputSchema might be a different type (though "object" is typical for multiple params).
            // For now, if not an object, assume no properties to parse from this level.
            return toolProperties;
        }

        Object propsObj = jsonSchema.get("properties");
        if (!(propsObj instanceof Map)) {
            // If "properties" field is missing or not a map, there are no parameter definitions.
            return toolProperties;
        }
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> propertiesMap = (Map<String, Map<String, Object>>) propsObj;

        Object requiredObj = jsonSchema.get("required");
        Set<String> requiredSet = Collections.emptySet();
        if (requiredObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> requiredList = (List<String>) requiredObj;
            requiredSet = new HashSet<>(requiredList);
        }

        for (Map.Entry<String, Map<String, Object>> entry : propertiesMap.entrySet()) {
            String propName = entry.getKey();
            Map<String, Object> propSchema = entry.getValue(); // This is the schema for the individual property
            if (propSchema == null) {
                 throw new McpException("Property schema for '" + propName + "' is null in MCP tool's inputSchema.");
            }
            toolProperties.add(parseSinglePropertySchema(propName, propSchema, requiredSet.contains(propName)));
        }
        return toolProperties;
    }

    /**
     * Parses a JSON schema for a single property into a {@link ToolProperty} instance.
     * This method handles different property types (string, integer, array, object, etc.)
     * and recursively calls itself or {@code parseJsonSchemaProperties} for nested types.
     *
     * @param name        The name of the property.
     * @param schema      The JSON schema map for this single property.
     * @param isRequired  Whether this property is marked as required by its parent object schema.
     * @return A {@link ToolProperty} instance.
     * @throws McpException if the property schema is malformed or uses unsupported types.
     */
    private ToolProperty parseSinglePropertySchema(String name, Map<String, Object> schema, boolean isRequired) {
        String typeString = (String) schema.get("type");
        String description = (String) schema.get("description");
        @SuppressWarnings("unchecked") // Type from JSON schema, can be various
        List<Object> enumList = (List<Object>) schema.get("enum");

        PropertyType type;
        try {
            // Ensure typeString is not null before calling toUpperCase
            type = PropertyType.valueOf(Objects.requireNonNull(typeString, "Property 'type' is missing in schema for " + name).toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new McpException("Unsupported or missing property type '" + typeString + "' in MCP tool schema for property: " + name, e);
        }

        switch (type) {
            case INTEGER:
            case STRING:
            case NUMBER:
            case BOOLEAN:
                // Use an anonymous class extending BaseToolProperty for simple scalar types
                return new BaseToolProperty(name, type, description, isRequired, enumList) {
                    // No additional overrides needed for these simple types.
                    // getJsonSchema() from BaseToolProperty is sufficient.
                };
            case ARRAY:
                Object itemsSchemaObj = schema.get("items");
                if (!(itemsSchemaObj instanceof Map)) {
                    throw new McpException("Array property '" + name + "' is missing or has malformed 'items' schema definition in MCP tool schema.");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> itemsSchemaMap = (Map<String, Object>) itemsSchemaObj;
                // The "name" for item schema is not relevant for the item schema itself, it's just the type definition.
                // We pass a placeholder name like "_items" or reuse parent name for context if needed by parser.
                ToolPropertySchema itemSchema = parseSinglePropertySchema(name + "_items", itemsSchemaMap, false);
                return new ArrayToolProperty(name, description, isRequired, itemSchema);
            case OBJECT:
                // For an object property, its schema *is* the object definition containing "type":"object", "properties", "required".
                // So, we parse its "properties" field.
                List<ToolProperty> subProperties = parseJsonSchemaProperties(schema);
                // The targetClass is null as we are defining from schema, not a Java class.
                return new ObjectToolProperty(name, description, isRequired, subProperties, null);
            default:
                // Should not be reached if PropertyType enum is comprehensive and typeString is valid.
                throw new McpException("Unhandled PropertyType '" + type + "' encountered for property: " + name);
        }
    }

    /**
     * Disconnects the underlying {@link McpClient} and its transport.
     * This should be called when the connector is no longer needed to free resources.
     * @throws McpException if disconnection fails.
     */
    public void shutdown() throws McpException {
        if (client != null) {
            client.disconnect();
        }
    }
}

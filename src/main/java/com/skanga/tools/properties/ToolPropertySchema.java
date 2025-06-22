package com.skanga.tools.properties;

import java.util.Map;

/**
 * Represents any entity that can be described by a JSON schema.
 * This is a fundamental interface for defining the structure of tool parameters,
 * object properties, or array item types within the tool framework.
 *
 * Implementations are responsible for providing their structure as a map
 * conforming to JSON Schema specifications. This map is typically used by
 * AI providers to understand the expected input for tools or functions.
 */
public interface ToolPropertySchema {
    /**
     * Gets the JSON schema representation of this property or entity.
     * This schema defines the structure, type, and constraints of the data.
     *
     * <p>Example for a simple string property:</p>
     * <pre>{@code
     * {
     *   "type": "string",
     *   "description": "The user's name."
     * }
     * }</pre>
     *
     * <p>Example for an object property:</p>
     * <pre>{@code
     * {
     *   "type": "object",
     *   "properties": {
     *     "location": {"type": "string", "description": "City and state."},
     *     "unit": {"type": "string", "enum": ["celsius", "fahrenheit"]}
     *   },
     *   "required": ["location"]
     * }
     * }</pre>
     *
     * @return A {@code Map<String, Object>} representing the JSON schema.
     *         The keys and values in the map should conform to JSON Schema draft specifications
     *         (e.g., "type", "description", "properties", "items", "required", "enum").
     */
    Map<String, Object> getJsonSchema();

    /**
     * Gets a map representation of the entire property definition, potentially including
     * more details than just the raw JSON schema (e.g., name, requirement status at its level).
     * This is primarily intended for serialization or debugging of the property's full definition.
     *
     * <p>For a {@link ToolProperty}, this would include its name, description, type,
     * requirement status, and potentially its schema (if it's a nested type like object/array).</p>
     * <p>For a schema that only defines the type of array items or a simple type,
     * this might return the same as {@link #getJsonSchema()} or a slightly wrapped version.</p>
     *
     * @return A {@code Map<String, Object>} suitable for JSON serialization, representing the full
     *         description of the property or schema entity.
     */
    Map<String, Object> toJsonSerializableMap();
}

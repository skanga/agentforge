package com.skanga.tools.properties;

/**
 * Represents a single named property, typically used to define a parameter
 * for a {@link com.skanga.tools.Tool} or a field within an {@link ObjectToolProperty}.
 *
 * It extends {@link ToolPropertySchema} as each property also defines its own schema
 * (e.g., a string property has a schema `{"type": "string"}`).
 */
public interface ToolProperty extends ToolPropertySchema {
    /**
     * Gets the name of the property.
     * This is used as the key in JSON objects for tool arguments or object definitions.
     * @return The property name.
     */
    String getName();

    /**
     * Gets the data type of this property.
     * Renamed from `getType()` in PHP to avoid potential clashes if this were a concrete class
     * with a generic `getType()` method (e.g., from a base class for typed values).
     * @return The {@link PropertyType} enum value (e.g., STRING, INTEGER, OBJECT).
     */
    PropertyType getPropertyType();

    /**
     * Gets the human-readable description of what this property represents.
     * This is used by AI models to understand the purpose of the property/parameter.
     * @return The description string, or null if not provided.
     */
    String getDescription();

    /**
     * Indicates whether this property is required when its containing object or tool parameters
     * are being defined.
     * @return True if the property is required, false otherwise.
     */
    boolean isRequired();
}

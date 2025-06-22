package com.skanga.tools.properties;

/**
 * Defines the data type of a {@link ToolProperty}.
 * This enum helps in defining the JSON schema for tool parameters
 * and can be used for validation or input processing.
 */
public enum PropertyType {
    /** Represents an integer type. Maps to "integer" in JSON schema. */
    INTEGER("integer"),
    /** Represents a string type. Maps to "string" in JSON schema. */
    STRING("string"),
    /** Represents a floating-point or decimal number type. Maps to "number" in JSON schema. */
    NUMBER("number"),
    /** Represents a boolean type (true/false). Maps to "boolean" in JSON schema. */
    BOOLEAN("boolean"),
    /** Represents a list or sequence of items. Maps to "array" in JSON schema.
     * The type of items in the array is defined by an associated {@link ToolPropertySchema}
     * in {@link ArrayToolProperty}. */
    ARRAY("array"),
    /** Represents a structured object with named properties. Maps to "object" in JSON schema.
     * The structure of the object is defined by a list of {@link ToolProperty} instances
     * in {@link ObjectToolProperty}. */
    OBJECT("object");

    private final String jsonSchemaType;

    /**
     * Constructor for PropertyType.
     * @param jsonSchemaType The string representation of this type in JSON schema.
     */
    PropertyType(String jsonSchemaType) {
        this.jsonSchemaType = jsonSchemaType;
    }

    /**
     * Gets the JSON schema type string for this property type.
     * For example, for {@code PropertyType.INTEGER}, this returns "integer".
     * @return The JSON schema type string.
     */
    public String getJsonSchemaType() {
        return jsonSchemaType;
    }
}

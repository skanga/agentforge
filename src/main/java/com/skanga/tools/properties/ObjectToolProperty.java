package com.skanga.tools.properties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap; // Preserves insertion order for properties in schema
import java.util.List;
import java.util.Map;
// import java.util.Objects; // Already imported by BaseToolProperty
import java.util.stream.Collectors;

/**
 * Represents a tool property of type "object".
 * It extends {@link BaseToolProperty} and defines a structure containing a list
 * of named sub-properties, each being a {@link ToolProperty} itself.
 *
 * Example JSON Schema generated:
 * <pre>{@code
 * {
 *   "type": "object",
 *   "description": "User details.",
 *   "properties": {
 *     "name": {"type": "string", "description": "User's full name."},
 *     "age": {"type": "integer", "description": "User's age."}
 *   },
 *   "required": ["name"]
 * }
 * }</pre>
 *
 * The `targetClass` field is a placeholder for potential future enhancements where this
 * property could be automatically mapped to/from a specific Java class, but currently,
 * schema generation is based on the explicitly provided `properties` list.
 */
public class ObjectToolProperty extends BaseToolProperty {
    /** List of sub-properties that define the structure of this object. */
    private final List<ToolProperty> properties;
    /**
     * Optional: The Java class that this object property might map to.
     * Currently unused for schema generation but can be useful for deserialization or type hints.
     * The PHP version had more elaborate logic for generating schema from a class via reflection,
     * which is deferred in this Java port (schema defined by explicitly adding ToolProperty instances).
     */
    private final Class<?> targetClass;

    /**
     * Constructs an ObjectToolProperty.
     *
     * @param name         The name of the object property.
     * @param description  A human-readable description.
     * @param required     True if this object property is required.
     * @param properties   A list of {@link ToolProperty} instances defining the sub-properties
     *                     of this object. A defensive copy is made. Can be null for an empty object.
     * @param targetClass  Optional: The Java class this object represents. Can be null.
     */
    public ObjectToolProperty(String name, String description, boolean required, List<ToolProperty> properties, Class<?> targetClass) {
        super(name, PropertyType.OBJECT, description, required);
        this.properties = (properties == null) ? Collections.emptyList() : new ArrayList<>(properties);
        this.targetClass = targetClass;
    }

    /**
     * Constructs an ObjectToolProperty without a target class.
     *
     * @param name         The name of the object property.
     * @param description  A human-readable description.
     * @param required     True if this object property is required.
     * @param properties   A list of {@link ToolProperty} instances defining the sub-properties.
     */
    public ObjectToolProperty(String name, String description, boolean required, List<ToolProperty> properties) {
        this(name, description, required, properties, null);
    }


    /**
     * Gets the list of sub-properties defining the structure of this object.
     * @return An unmodifiable list of {@link ToolProperty} instances.
     */
    public List<ToolProperty> getProperties() {
        return Collections.unmodifiableList(properties);
    }

    /**
     * Gets the optional target Java class associated with this object property.
     * @return The target class, or null if not specified.
     */
    public Class<?> getTargetClass() {
        return targetClass;
    }

    /**
     * Helper method to get a list of names of all sub-properties that are marked as required.
     * @return A list of required sub-property names.
     */
    public List<String> getRequiredProperties() {
        return properties.stream()
                .filter(ToolProperty::isRequired)
                .map(ToolProperty::getName)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * <p>
     * For an object property, this method extends the base schema (type, description)
     * by adding a "properties" field (a map of sub-property names to their JSON schemas)
     * and a "required" field (a list of names of required sub-properties).
     * </p>
     */
    @Override
    public Map<String, Object> getJsonSchema() {
        Map<String, Object> schema = super.getJsonSchema(); // Gets type="object", description

        Map<String, Object> subPropertiesSchema = new LinkedHashMap<>();
        for (ToolProperty prop : properties) {
            // Each sub-property provides its own JSON schema
            subPropertiesSchema.put(prop.getName(), prop.getJsonSchema());
        }

        if (!subPropertiesSchema.isEmpty()) {
            schema.put("properties", subPropertiesSchema);
        }

        List<String> requiredSubProps = getRequiredProperties();
        if (!requiredSubProps.isEmpty()) {
            schema.put("required", requiredSubProps);
        }

        return schema;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Serializes the object property definition, including its name, type ("object"),
     * description, requirement status, and a list of its sub-property definitions
     * (obtained from their {@code toJsonSerializableMap()} method).
     * </p>
     */
    @Override
    public Map<String, Object> toJsonSerializableMap() {
        Map<String, Object> map = super.toJsonSerializableMap(); // name, description, type, required (for this object itself)

        if (!properties.isEmpty()) {
            // Serialize each sub-property fully
            List<Map<String, Object>> subPropertiesList = properties.stream()
                    .map(ToolProperty::toJsonSerializableMap)
                    .collect(Collectors.toList());
            map.put("properties_definitions", subPropertiesList); // Using a different key to distinguish from schema's "properties"
        }
        return map;
    }
}

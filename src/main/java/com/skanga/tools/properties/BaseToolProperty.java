package com.skanga.tools.properties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap; // Preserves insertion order for schema properties
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An abstract base class implementing common functionalities for {@link ToolProperty}.
 * This class handles storing the name, property type, description, requirement status,
 * and an optional list of enum values.
 *
 * It provides default implementations for {@link #getJsonSchema()} and
 * {@link #toJsonSerializableMap()} suitable for simple scalar types (string, integer, number, boolean).
 * Subclasses like {@link ArrayToolProperty} and {@link ObjectToolProperty} override
 * {@link #getJsonSchema()} to provide more complex schema structures for "array" and "object" types.
 *
 * Note: This class is abstract primarily because the intent is for specific property types
 * (like Array or Object, or custom scalar types if needed) to extend it, or for simple
 * anonymous inner classes to be used for basic scalar properties within {@link com.skanga.tools.BaseTool}.
 */
public abstract class BaseToolProperty implements ToolProperty {
    /** The name of the property. */
    protected final String name;
    /** The data type of the property. */
    protected final PropertyType propertyType;
    /** A human-readable description of the property. */
    protected final String description;
    /** Whether this property is required. */
    protected final boolean required;
    /** An optional list of allowed enum values for this property. */
    protected final List<Object> enumList;

    /**
     * Constructs a BaseToolProperty.
     *
     * @param name         The name of the property. Must not be null.
     * @param propertyType The {@link PropertyType} of the property. Must not be null.
     * @param description  A human-readable description. Can be null.
     * @param required     True if this property is required, false otherwise.
     * @param enumList     An optional list of allowed enum values. If null or empty, no enum constraint is applied.
     *                     A defensive copy of the list is made.
     */
    public BaseToolProperty(String name, PropertyType propertyType, String description, boolean required, List<Object> enumList) {
        Objects.requireNonNull(name, "Property name cannot be null.");
        Objects.requireNonNull(propertyType, "Property type cannot be null.");
        this.name = name;
        this.propertyType = propertyType;
        this.description = description;
        this.required = required;
        this.enumList = (enumList == null) ? Collections.emptyList() : new ArrayList<>(enumList);
    }

    /**
     * Constructs a BaseToolProperty without an enum list.
     *
     * @param name         The name of the property.
     * @param propertyType The {@link PropertyType} of the property.
     * @param description  A human-readable description.
     * @param required     True if this property is required.
     */
    public BaseToolProperty(String name, PropertyType propertyType, String description, boolean required) {
        this(name, propertyType, description, required, null);
    }


    @Override
    public String getName() {
        return name;
    }

    @Override
    public PropertyType getPropertyType() {
        return propertyType;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean isRequired() {
        return required;
    }

    /**
     * Gets the list of allowed enum values for this property.
     * @return An unmodifiable list of enum values, or an empty list if no enum constraint is set.
     */
    public List<Object> getEnumList() {
        // Return a copy to prevent external modification if original list was mutable
        return Collections.unmodifiableList(enumList);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This base implementation generates a JSON schema suitable for scalar types
     * (string, integer, number, boolean), including their description and enum constraints if provided.
     * Subclasses for complex types like "array" or "object" should override this to add
     * type-specific schema attributes (e.g., "items", "properties").
     * </p>
     */
    @Override
    public Map<String, Object> getJsonSchema() {
        Map<String, Object> schema = new LinkedHashMap<>(); // Use LinkedHashMap to maintain order
        schema.put("type", propertyType.getJsonSchemaType());
        if (description != null && !description.isEmpty()) {
            schema.put("description", description);
        }
        if (enumList != null && !enumList.isEmpty()) {
            schema.put("enum", new ArrayList<>(enumList)); // Ensure a mutable copy for the schema if needed
        }
        return schema;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This base implementation serializes the core attributes of a property:
     * name, description, type (as JSON schema string), enum values, and its requirement status.
     * This map is suitable for representing a property within a list of properties, for example.
     * </p>
     */
    @Override
    public Map<String, Object> toJsonSerializableMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name); // Name is part of this serialization, unlike getJsonSchema()
        if (description != null && !description.isEmpty()) {
            map.put("description", description);
        }
        map.put("type", propertyType.getJsonSchemaType());
        if (enumList != null && !enumList.isEmpty()) {
            map.put("enum", new ArrayList<>(enumList));
        }
        map.put("required", required); // Requirement status of this property itself

        // Note: For use as a schema definition within an object's "properties" map,
        // typically only the output of getJsonSchema() would be used as the value for this property's name.
        // This toJsonSerializableMap() provides a more complete definition if serializing the property itself.
        return map;
    }
}

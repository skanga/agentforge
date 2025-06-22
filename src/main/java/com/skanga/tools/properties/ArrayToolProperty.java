package com.skanga.tools.properties;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a tool property of type "array".
 * It extends {@link BaseToolProperty} and specifies the schema for the items
 * contained within the array using another {@link ToolPropertySchema}.
 *
 * Example JSON Schema generated:
 * <pre>{@code
 * {
 *   "type": "array",
 *   "description": "A list of tags.",
 *   "items": {
 *     "type": "string"
 *   }
 * }
 * }</pre>
 */
public class ArrayToolProperty extends BaseToolProperty {
    /**
     * The schema definition for the items that this array can contain.
     * For example, if this is an array of strings, `itemsSchema` would typically
     * be a simple property schema defining `{"type": "string"}`. If it's an
     * array of objects, `itemsSchema` would be an {@link ObjectToolProperty}
     * or a schema map defining that object structure.
     */
    private final ToolPropertySchema itemsSchema;

    /**
     * Constructs an ArrayToolProperty.
     *
     * @param name         The name of the array property.
     * @param description  A human-readable description.
     * @param required     True if this array property is required.
     * @param itemsSchema  The schema defining the type of items in this array. Must not be null.
     *                     This schema itself does not have a name in this context, it just defines item structure.
     */
    public ArrayToolProperty(String name, String description, boolean required, ToolPropertySchema itemsSchema) {
        super(name, PropertyType.ARRAY, description, required);
        Objects.requireNonNull(itemsSchema, "Items schema for ArrayToolProperty cannot be null.");
        this.itemsSchema = itemsSchema;
    }

    /**
     * Gets the schema definition for the items in this array.
     * @return The {@link ToolPropertySchema} for the array items.
     */
    public ToolPropertySchema getItemsSchema() {
        return itemsSchema;
    }

    /**
     * {@inheritDoc}
     * <p>
     * For an array property, this method extends the base schema (type, description)
     * by adding an "items" field, which contains the JSON schema of the elements
     * allowed in this array (obtained from {@link #getItemsSchema()}).
     * </p>
     */
    @Override
    public Map<String, Object> getJsonSchema() {
        Map<String, Object> schema = super.getJsonSchema(); // Gets type="array", description, enum (if any on array itself)
        schema.put("items", itemsSchema.getJsonSchema()); // Add schema for items in the array
        return schema;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Serializes the array property definition, including its name, type ("array"),
     * description, requirement status, and the schema of its items.
     * </p>
     */
    @Override
    public Map<String, Object> toJsonSerializableMap() {
        Map<String, Object> map = super.toJsonSerializableMap(); // name, description, type, required
        // It might be more common to serialize the item schema directly for "items"
        map.put("items", itemsSchema.getJsonSchema());
        // Or, if itemsSchema.toJsonSerializableMap() provides a more complete definition for items (e.g., if items were named):
        // map.put("items", itemsSchema.toJsonSerializableMap());
        return map;
    }
}

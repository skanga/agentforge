package com.skanga.tools.properties;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ArrayToolPropertyTests {

    // Simple concrete schema for items (e.g., string items)
    static class StringItemSchema extends BaseToolProperty {
        public StringItemSchema() {
            super("_item", PropertyType.STRING, "A string item", false); // Name for item schema itself is not used by Array prop
        }
    }

    // More complex object item schema for testing
    static class ObjectItemSchema extends BaseToolProperty {
         private final List<ToolProperty> itemProperties;
        public ObjectItemSchema() {
            super("_item_object", PropertyType.OBJECT, "An object item", false);
            itemProperties = List.of(
                new BaseToolProperty("id", PropertyType.INTEGER, "Item ID", true) {},
                new BaseToolProperty("value", PropertyType.STRING, "Item value", false) {}
            );
        }

        @Override
        public Map<String, Object> getJsonSchema() {
            Map<String, Object> schema = super.getJsonSchema();
            Map<String, Object> props = new LinkedHashMap<>();
            for (ToolProperty p : itemProperties) {
                props.put(p.getName(), p.getJsonSchema());
            }
            schema.put("properties", props);
            schema.put("required", List.of("id"));
            return schema;
        }
    }


    @Test
    void constructor_andGetters_workCorrectly() {
        String name = "tags";
        String description = "List of tags.";
        boolean required = true;
        ToolPropertySchema itemSchema = new StringItemSchema();

        ArrayToolProperty prop = new ArrayToolProperty(name, description, required, itemSchema);

        assertEquals(name, prop.getName());
        assertEquals(PropertyType.ARRAY, prop.getPropertyType());
        assertEquals(description, prop.getDescription());
        assertEquals(required, prop.isRequired());
        assertSame(itemSchema, prop.getItemsSchema());
    }

    @Test
    void constructor_nullItemSchema_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            new ArrayToolProperty("list", "desc", false, null);
        });
    }


    @Test
    void getJsonSchema_withSimpleStringItemSchema_isCorrect() {
        ArrayToolProperty prop = new ArrayToolProperty("keywords", "Search keywords", false, new StringItemSchema());
        Map<String, Object> schema = prop.getJsonSchema();

        assertEquals("array", schema.get("type"));
        assertEquals("Search keywords", schema.get("description"));
        assertTrue(schema.containsKey("items"));

        @SuppressWarnings("unchecked")
        Map<String, Object> itemsPart = (Map<String, Object>) schema.get("items");
        assertEquals("string", itemsPart.get("type"));
        assertEquals("A string item", itemsPart.get("description")); // From StringItemSchema
    }

    @Test
    @SuppressWarnings("unchecked")
    void getJsonSchema_withObjectItemSchema_isCorrect() {
        ArrayToolProperty prop = new ArrayToolProperty("users", "List of users", true, new ObjectItemSchema());
        Map<String, Object> schema = prop.getJsonSchema();

        assertEquals("array", schema.get("type"));
        assertEquals("List of users", schema.get("description"));
        assertTrue(schema.containsKey("items"));

        Map<String, Object> itemsPart = (Map<String, Object>) schema.get("items");
        assertEquals("object", itemsPart.get("type"));
        assertEquals("An object item", itemsPart.get("description"));

        Map<String, Object> itemProps = (Map<String, Object>) itemsPart.get("properties");
        assertNotNull(itemProps);
        assertTrue(itemProps.containsKey("id"));
        assertTrue(itemProps.containsKey("value"));
        assertEquals("integer", ((Map<String,Object>)itemProps.get("id")).get("type"));

        List<String> requiredItems = (List<String>) itemsPart.get("required");
        assertTrue(requiredItems.contains("id"));
    }

    @Test
    void toJsonSerializableMap_includesItemsSchema() {
        ArrayToolProperty prop = new ArrayToolProperty("tags", "List of tags", true, new StringItemSchema());
        Map<String, Object> map = prop.toJsonSerializableMap();

        assertEquals("tags", map.get("name"));
        assertEquals("array", map.get("type"));
        assertTrue(map.containsKey("items"));

        // Check if items is the JSON schema of the item type
        @SuppressWarnings("unchecked")
        Map<String, Object> itemsPart = (Map<String, Object>) map.get("items");
        assertEquals("string", itemsPart.get("type"));
    }
}

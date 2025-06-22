package com.skanga.tools.properties;

import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ObjectToolPropertyTests {

    // Concrete implementation for simple properties used within an object
    static class SimpleStringProperty extends BaseToolProperty {
        public SimpleStringProperty(String name, String description, boolean required) {
            super(name, PropertyType.STRING, description, required);
        }
    }
    static class SimpleIntegerProperty extends BaseToolProperty {
        public SimpleIntegerProperty(String name, String description, boolean required) {
            super(name, PropertyType.INTEGER, description, required);
        }
    }


    @Test
    void constructor_andGetters_workCorrectly() {
        String name = "userProfile";
        String description = "User profile details.";
        boolean required = true;
        List<ToolProperty> subProps = List.of(new SimpleStringProperty("name", "User's name", true));
        Class<?> targetClass = Map.class; // Example target class

        ObjectToolProperty prop = new ObjectToolProperty(name, description, required, subProps, targetClass);

        assertEquals(name, prop.getName());
        assertEquals(PropertyType.OBJECT, prop.getPropertyType());
        assertEquals(description, prop.getDescription());
        assertEquals(required, prop.isRequired());
        assertEquals(subProps, prop.getProperties());
        assertEquals(targetClass, prop.getTargetClass());
    }

    @Test
    void constructor_nullPropertiesList_initializesEmptyList() {
        ObjectToolProperty prop = new ObjectToolProperty("emptyObj", "Desc", false, null, null);
        assertNotNull(prop.getProperties());
        assertTrue(prop.getProperties().isEmpty());
    }

    @Test
    void getRequiredProperties_returnsCorrectNames() {
        ToolProperty nameProp = new SimpleStringProperty("name", "User's name", true);
        ToolProperty ageProp = new SimpleIntegerProperty("age", "User's age", false);
        ToolProperty emailProp = new SimpleStringProperty("email", "User's email", true);
        List<ToolProperty> subProps = List.of(nameProp, ageProp, emailProp);

        ObjectToolProperty userProfile = new ObjectToolProperty("user", "User data", true, subProps);
        List<String> required = userProfile.getRequiredProperties();

        assertEquals(2, required.size());
        assertTrue(required.contains("name"));
        assertTrue(required.contains("email"));
        assertFalse(required.contains("age"));
    }

    @Test
    void getRequiredProperties_noRequiredSubProperties_returnsEmptyList() {
        ToolProperty nameProp = new SimpleStringProperty("name", "User's name", false);
        ObjectToolProperty objProp = new ObjectToolProperty("obj", "Desc", true, List.of(nameProp));
        assertTrue(objProp.getRequiredProperties().isEmpty());
    }


    @Test
    @SuppressWarnings("unchecked")
    void getJsonSchema_withSubProperties_isCorrect() {
        ToolProperty nameProp = new SimpleStringProperty("username", "Login name", true);
        ToolProperty itemsProp = new ArrayToolProperty("tags", "User tags", false, new BaseToolProperty("_item", PropertyType.STRING, "", false) {});
        List<ToolProperty> subProps = List.of(nameProp, itemsProp);

        ObjectToolProperty userDetails = new ObjectToolProperty("details", "User details object", true, subProps);
        Map<String, Object> schema = userDetails.getJsonSchema();

        assertEquals("object", schema.get("type"));
        assertEquals("User details object", schema.get("description"));

        Map<String, Object> propertiesField = (Map<String, Object>) schema.get("properties");
        assertNotNull(propertiesField);
        assertEquals(2, propertiesField.size());

        assertTrue(propertiesField.containsKey("username"));
        Map<String, Object> usernameSchema = (Map<String, Object>) propertiesField.get("username");
        assertEquals("string", usernameSchema.get("type"));
        assertEquals("Login name", usernameSchema.get("description"));

        assertTrue(propertiesField.containsKey("tags"));
        Map<String, Object> tagsSchema = (Map<String, Object>) propertiesField.get("tags");
        assertEquals("array", tagsSchema.get("type"));
        assertEquals("User tags", tagsSchema.get("description"));
        assertNotNull(tagsSchema.get("items")); // ArrayToolProperty adds "items"

        List<String> requiredField = (List<String>) schema.get("required");
        assertNotNull(requiredField);
        assertEquals(1, requiredField.size()); // Only "username" was required from subProps
        assertTrue(requiredField.contains("username"));
    }

    @Test
    void getJsonSchema_noSubProperties_isEmptyObjectSchema() {
        ObjectToolProperty emptyObj = new ObjectToolProperty("empty", "An empty object", false, Collections.emptyList());
        Map<String, Object> schema = emptyObj.getJsonSchema();

        assertEquals("object", schema.get("type"));
        assertFalse(schema.containsKey("properties"));
        assertFalse(schema.containsKey("required"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toJsonSerializableMap_includesSubPropertyDefinitions() {
        ToolProperty nameProp = new SimpleStringProperty("id", "Identifier", true);
        ObjectToolProperty dataObj = new ObjectToolProperty("data", "Data object", true, List.of(nameProp));
        Map<String, Object> map = dataObj.toJsonSerializableMap();

        assertEquals("data", map.get("name"));
        assertEquals("object", map.get("type"));
        assertEquals(true, map.get("required"));

        assertTrue(map.containsKey("properties_definitions"));
        List<Map<String, Object>> propsDefs = (List<Map<String, Object>>) map.get("properties_definitions");
        assertEquals(1, propsDefs.size());
        assertEquals("id", propsDefs.get(0).get("name"));
        assertEquals("string", propsDefs.get(0).get("type"));
    }
}

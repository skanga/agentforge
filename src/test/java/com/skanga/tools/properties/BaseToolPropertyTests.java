package com.skanga.tools.properties;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BaseToolPropertyTests {

    // Concrete implementation for testing abstract BaseToolProperty
    static class ConcreteToolProperty extends BaseToolProperty {
        public ConcreteToolProperty(String name, PropertyType propertyType, String description, boolean required, List<Object> enumList) {
            super(name, propertyType, description, required, enumList);
        }
         public ConcreteToolProperty(String name, PropertyType propertyType, String description, boolean required) {
            super(name, propertyType, description, required);
        }
    }

    @Test
    void constructor_andGetters_workCorrectly() {
        String name = "testProp";
        PropertyType type = PropertyType.STRING;
        String description = "A test property.";
        boolean required = true;
        List<Object> enumValues = List.of("a", "b", "c");

        ConcreteToolProperty prop = new ConcreteToolProperty(name, type, description, required, enumValues);

        assertEquals(name, prop.getName());
        assertEquals(type, prop.getPropertyType());
        assertEquals(description, prop.getDescription());
        assertEquals(required, prop.isRequired());
        assertEquals(enumValues, prop.getEnumList());
    }

    @Test
    void constructor_withoutEnumList_initializesEmptyEnumList() {
        ConcreteToolProperty prop = new ConcreteToolProperty("noEnum", PropertyType.INTEGER, "Desc", false);
        assertNotNull(prop.getEnumList());
        assertTrue(prop.getEnumList().isEmpty());
    }

    @Test
    void constructor_nullEnumList_initializesEmptyEnumList() {
        ConcreteToolProperty prop = new ConcreteToolProperty("nullEnum", PropertyType.NUMBER, "Desc", true, null);
        assertNotNull(prop.getEnumList());
        assertTrue(prop.getEnumList().isEmpty());
    }


    @Test
    void getJsonSchema_forStringType_isCorrect() {
        ConcreteToolProperty prop = new ConcreteToolProperty("username", PropertyType.STRING, "User's login name", true);
        Map<String, Object> schema = prop.getJsonSchema();

        assertEquals("string", schema.get("type"));
        assertEquals("User's login name", schema.get("description"));
        assertFalse(schema.containsKey("enum"));
    }

    @Test
    void getJsonSchema_forIntegerTypeWithEnum_isCorrect() {
        List<Object> priorityValues = List.of(1, 2, 3, 4, 5);
        ConcreteToolProperty prop = new ConcreteToolProperty("priority", PropertyType.INTEGER, "Task priority", false, priorityValues);
        Map<String, Object> schema = prop.getJsonSchema();

        assertEquals("integer", schema.get("type"));
        assertEquals("Task priority", schema.get("description"));
        assertEquals(priorityValues, schema.get("enum"));
    }

    @Test
    void getJsonSchema_forBooleanType_isCorrect() {
        ConcreteToolProperty prop = new ConcreteToolProperty("isActive", PropertyType.BOOLEAN, "Is the user active?", true);
        Map<String, Object> schema = prop.getJsonSchema();

        assertEquals("boolean", schema.get("type"));
        assertEquals("Is the user active?", schema.get("description"));
    }

    @Test
    void getJsonSchema_nullDescription_descriptionNotIncluded() {
        ConcreteToolProperty prop = new ConcreteToolProperty("value", PropertyType.NUMBER, null, false);
        Map<String, Object> schema = prop.getJsonSchema();
        assertFalse(schema.containsKey("description"));
    }

    @Test
    void getJsonSchema_emptyDescription_descriptionNotIncluded() {
        // The code correctly omits description if it's null or empty.
        // The test should assert this behavior.
        ConcreteToolProperty prop = new ConcreteToolProperty("value", PropertyType.NUMBER, "", false);
        Map<String, Object> schema = prop.getJsonSchema();
        assertFalse(schema.containsKey("description"), "Description key should not be included if the description string is empty.");
    }


    @Test
    void toJsonSerializableMap_includesAllBaseFields() {
        List<Object> statusValues = List.of("pending", "active", "inactive");
        ConcreteToolProperty prop = new ConcreteToolProperty("status", PropertyType.STRING, "User status", true, statusValues);
        Map<String, Object> map = prop.toJsonSerializableMap();

        assertEquals("status", map.get("name"));
        assertEquals("string", map.get("type"));
        assertEquals("User status", map.get("description"));
        assertEquals(true, map.get("required"));
        assertEquals(statusValues, map.get("enum"));
    }

    @Test
    void constructor_nullName_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            new ConcreteToolProperty(null, PropertyType.STRING, "desc", true);
        });
    }

    @Test
    void constructor_nullType_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            new ConcreteToolProperty("name", null, "desc", true);
        });
    }
}

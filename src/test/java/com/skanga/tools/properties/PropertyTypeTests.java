package com.skanga.tools.properties;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PropertyTypeTests {

    @Test
    void enumValues_exist() {
        assertNotNull(PropertyType.valueOf("STRING"));
        assertNotNull(PropertyType.valueOf("INTEGER"));
        assertNotNull(PropertyType.valueOf("NUMBER"));
        assertNotNull(PropertyType.valueOf("BOOLEAN"));
        assertNotNull(PropertyType.valueOf("ARRAY"));
        assertNotNull(PropertyType.valueOf("OBJECT"));
    }

    @Test
    void getJsonSchemaType_returnsCorrectString() {
        assertEquals("string", PropertyType.STRING.getJsonSchemaType());
        assertEquals("integer", PropertyType.INTEGER.getJsonSchemaType());
        assertEquals("number", PropertyType.NUMBER.getJsonSchemaType());
        assertEquals("boolean", PropertyType.BOOLEAN.getJsonSchemaType());
        assertEquals("array", PropertyType.ARRAY.getJsonSchemaType());
        assertEquals("object", PropertyType.OBJECT.getJsonSchemaType());
    }

    @Test
    void enumToString_returnsEnumName() {
        // Default toString() for enums is their name.
        assertEquals("STRING", PropertyType.STRING.toString());
    }
}

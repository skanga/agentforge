package com.skanga.tools;

import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionInputTests {

    @Test
    void constructor_withArguments_storesCopy() {
        Map<String, Object> originalArgs = new HashMap<>();
        originalArgs.put("key1", "value1");
        originalArgs.put("key2", 123);

        ToolExecutionInput input = new ToolExecutionInput(originalArgs);
        assertEquals("value1", input.getArgument("key1"));
        assertEquals(123, input.getArgument("key2"));

        // Modify original map, should not affect input's map
        originalArgs.put("key3", "newValue");
        assertNull(input.getArgument("key3"));
        assertEquals(2, input.arguments().size());
    }

    @Test
    void constructor_withNullArguments_createsEmptyMap() {
        ToolExecutionInput input = new ToolExecutionInput(null);
        assertNotNull(input.arguments());
        assertTrue(input.arguments().isEmpty());
    }

    @Test
    void constructor_withEmptyArguments_createsEmptyMap() {
        ToolExecutionInput input = new ToolExecutionInput(Collections.emptyMap());
        assertNotNull(input.arguments());
        assertTrue(input.arguments().isEmpty());
    }

    @Test
    void arguments_returnedMapIsUnmodifiable() {
        Map<String, Object> originalArgs = new HashMap<>();
        originalArgs.put("key1", "value1");
        ToolExecutionInput input = new ToolExecutionInput(originalArgs);

        Map<String, Object> retrievedArgs = input.arguments();
        assertThrows(UnsupportedOperationException.class, () -> {
            retrievedArgs.put("newKey", "newValue");
        });
    }


    @Test
    void getArgument_existingKey_returnsValue() {
        Map<String, Object> args = Map.of("city", "London", "days", 5);
        ToolExecutionInput input = new ToolExecutionInput(args);
        assertEquals("London", input.getArgument("city"));
        assertEquals(5, input.getArgument("days"));
    }

    @Test
    void getArgument_nonExistingKey_returnsNull() {
        Map<String, Object> args = Map.of("city", "London");
        ToolExecutionInput input = new ToolExecutionInput(args);
        assertNull(input.getArgument("country"));
    }

    @Test
    void getArgumentOrDefault_existingKey_returnsValue() {
        Map<String, Object> args = Map.of("name", "Alice");
        ToolExecutionInput input = new ToolExecutionInput(args);
        assertEquals("Alice", input.getArgumentOrDefault("name", "Bob"));
    }

    @Test
    void getArgumentOrDefault_nonExistingKey_returnsDefaultValue() {
        Map<String, Object> args = Map.of("name", "Alice");
        ToolExecutionInput input = new ToolExecutionInput(args);
        assertEquals("Default", input.getArgumentOrDefault("location", "Default"));
        assertEquals(100, (Integer) input.getArgumentOrDefault("count", 100));
    }

    @Test
    void getArgumentOrDefault_nullDefaultValue_returnsNullIfKeyMissing() {
        ToolExecutionInput input = new ToolExecutionInput(Collections.emptyMap());
        assertNull(input.getArgumentOrDefault("key", null));
    }
}

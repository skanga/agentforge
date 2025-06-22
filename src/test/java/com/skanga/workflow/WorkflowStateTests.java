package com.skanga.workflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowStateTests {

    private WorkflowState state;

    @BeforeEach
    void setUp() {
        state = new WorkflowState();
    }

    @Test
    void constructor_noArgs_initializesEmptyState() {
        assertTrue(state.getAll().isEmpty());
    }

    @Test
    void constructor_withInitialData_initializesWithCopy() {
        Map<String, Object> initialData = new HashMap<>();
        initialData.put("key1", "value1");
        initialData.put("count", 100);

        WorkflowState initState = new WorkflowState(initialData);

        assertEquals("value1", initState.get("key1"));
        assertEquals(100, initState.get("count"));
        assertEquals(2, initState.getAll().size());

        // Modify original map, should not affect WorkflowState
        initialData.put("key2", "newValue");
        assertNull(initState.get("key2"));
    }

    @Test
    void constructor_withNullInitialData_initializesEmptyState() {
        WorkflowState initState = new WorkflowState(null);
        assertTrue(initState.getAll().isEmpty());
    }


    @Test
    void put_andGet_storesAndRetrievesValues() {
        state.put("name", "WorkflowAlpha");
        state.put("step", 2);
        state.put("isActive", true);
        state.put("config", Map.of("timeout", 5000));

        assertEquals("WorkflowAlpha", state.get("name"));
        assertEquals(2, state.get("step"));
        assertEquals(true, state.get("isActive"));
        assertEquals(Map.of("timeout", 5000), state.get("config"));
    }

    @Test
    void put_nullKey_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> state.put(null, "value"));
    }

    @Test
    void put_nullValue_isAllowed() {
        state.put("nullableKey", null);
        assertTrue(state.containsKey("nullableKey"));
        assertNull(state.get("nullableKey"));
    }


    @Test
    void get_nonExistentKey_returnsNull() {
        assertNull(state.get("nonExistent"));
    }

    @Test
    void getOrDefault_existingKey_returnsValue() {
        state.put("myKey", "myValue");
        assertEquals("myValue", state.getOrDefault("myKey", "defaultValue"));
    }

    @Test
    void getOrDefault_nonExistentKey_returnsDefaultValue() {
        assertEquals("defaultValue", state.getOrDefault("absentKey", "defaultValue"));
        assertEquals(123, (int) state.getOrDefault("absentIntKey", 123));
    }

    @Test
    void putAll_addsAllEntriesFromMap() {
        Map<String, Object> dataToAdd = new HashMap<>();
        dataToAdd.put("a", 1);
        dataToAdd.put("b", "two");
        state.put("initial", 0); // Existing value

        state.putAll(dataToAdd);

        assertEquals(0, state.get("initial"));
        assertEquals(1, state.get("a"));
        assertEquals("two", state.get("b"));
        assertEquals(3, state.getAll().size());
    }

    @Test
    void putAll_withOverlappingKeys_overwritesExisting() {
        state.put("a", 1);
        state.put("b", "old_b_value");

        Map<String, Object> dataToAdd = Map.of("b", "new_b_value", "c", 3.0);
        state.putAll(dataToAdd);

        assertEquals(1, state.get("a"));
        assertEquals("new_b_value", state.get("b"));
        assertEquals(3.0, state.get("c"));
    }

    @Test
    void putAll_nullMap_doesNothing() {
        state.put("a",1);
        assertDoesNotThrow(() -> state.putAll(null));
        assertEquals(1, state.getAll().size());
        assertEquals(1, state.get("a"));
    }


    @Test
    void containsKey_worksAsExpected() {
        state.put("presentKey", "present");
        assertTrue(state.containsKey("presentKey"));
        assertFalse(state.containsKey("absentKey"));
    }

    @Test
    void remove_existingKey_removesAndReturnsValue() {
        state.put("toRemove", "this will be removed");
        Object removedValue = state.remove("toRemove");
        assertEquals("this will be removed", removedValue);
        assertFalse(state.containsKey("toRemove"));
        assertNull(state.get("toRemove"));
    }

    @Test
    void remove_nonExistingKey_returnsNull() {
        assertNull(state.remove("nonExistentKey"));
    }

    @Test
    void getAll_returnsUnmodifiableMap() {
        state.put("key", "value");
        Map<String, Object> allData = state.getAll();
        assertEquals("value", allData.get("key"));
        assertThrows(UnsupportedOperationException.class, () -> allData.put("newKey", "newValue"));
    }

    @Test
    void copy_createsIndependentState() {
        state.put("key1", "originalValue1");
        Map<String, String> mutableMap = new HashMap<>();
        mutableMap.put("innerKey", "innerValue");
        state.put("key2_map", mutableMap);

        WorkflowState copiedState = state.copy();

        // Modify original state
        state.put("key1", "modifiedOriginalValue1");
        state.put("key3", "addedToOriginal");
        mutableMap.put("innerKey", "modifiedInnerValueInOriginal"); // Modify via original reference

        // Copied state should be unaffected by scalar/new key modifications
        assertEquals("originalValue1", copiedState.get("key1"));
        assertNull(copiedState.get("key3"));

        // Check shallow copy for mutable objects like maps
        // The map reference itself is copied, so internal changes are visible
        @SuppressWarnings("unchecked")
        Map<String, String> copiedInnerMap = (Map<String, String>) copiedState.get("key2_map");
        assertEquals("modifiedInnerValueInOriginal", copiedInnerMap.get("innerKey"));

        // Modify copied state's map
        copiedState.put("key1_copied", "addedToCopied");
        assertNull(state.get("key1_copied")); // Original state should not have this

        // Modify the inner map via copiedState's reference
        copiedInnerMap.put("newInnerKey", "valueInCopiedInnerMap");
        // This change WILL be reflected in the original state's map if it was the same object.
        // WorkflowState.copy() does new HashMap<>(this.stateData), which is a shallow copy of the map.
        // The Map object itself (mutableMap) is shared.
        assertEquals("valueInCopiedInnerMap", ((Map<?,?>)state.get("key2_map")).get("newInnerKey"));
        // This confirms WorkflowState.copy() is a shallow copy of the internal map's values.
        // Users should be aware if storing mutable objects in WorkflowState.
    }

    @Test
    void toString_providesSummary() {
        state.put("user_id", 123);
        state.put("session_data", Map.of("theme", "dark"));
        String str = state.toString();
        assertTrue(str.startsWith("WorkflowState{stateData_keys="));
        assertTrue(str.contains("user_id"));
        assertTrue(str.contains("session_data"));
        assertFalse(str.contains("123"), "Should not print values directly in default toString for brevity/security");
    }
}

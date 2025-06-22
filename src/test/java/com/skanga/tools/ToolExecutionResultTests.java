package com.skanga.tools;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionResultTests {

    @Test
    void constructor_withResult_storesResult() {
        String testResult = "Execution successful";
        ToolExecutionResult result = new ToolExecutionResult(testResult);
        assertEquals(testResult, result.result());
    }

    @Test
    void constructor_withNullResult_storesNull() {
        ToolExecutionResult result = new ToolExecutionResult(null);
        assertNull(result.result());
    }

    @Test
    void constructor_withComplexResultType_storesResult() {
        Map<String, Integer> complexResult = Map.of("status", 200, "items", 5);
        ToolExecutionResult result = new ToolExecutionResult(complexResult);
        assertEquals(complexResult, result.result());
        assertTrue(result.result() instanceof Map);
    }

    @Test
    void result_accessorReturnsCorrectValue() {
        Object objResult = new Object();
        ToolExecutionResult result = new ToolExecutionResult(objResult);
        assertSame(objResult, result.result());
    }
}

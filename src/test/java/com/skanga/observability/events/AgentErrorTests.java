package com.skanga.observability.events;

import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AgentErrorTests {

    @Test
    void constructor_allArgs_setsFieldsCorrectly() {
        Throwable t = new RuntimeException("Test Ex");
        Map<String, Object> context = Map.of("key", "value");
        AgentError event = new AgentError(t, true, "Critical failure", context);

        assertSame(t, event.exception());
        assertTrue(event.critical());
        assertEquals("Critical failure", event.message());
        assertEquals(context, event.context()); // Record constructor makes unmodifiable copy
        assertNotSame(context, event.context()); // Should be a copy
    }

    @Test
    void constructor_exceptionAndCritical_setsAndDefaultsOthers() {
        Throwable t = new RuntimeException("Test Ex");
        AgentError event = new AgentError(t, false);

        assertSame(t, event.exception());
        assertFalse(event.critical());
        assertNull(event.message());
        assertEquals(Collections.emptyMap(), event.context());
    }

    @Test
    void constructor_exceptionCriticalAndMessage_setsAndDefaultsContext() {
        Throwable t = new RuntimeException("Test Ex");
        AgentError event = new AgentError(t, true, "A message");

        assertSame(t, event.exception());
        assertTrue(event.critical());
        assertEquals("A message", event.message());
        assertEquals(Collections.emptyMap(), event.context());
    }


    @Test
    void constructor_nullException_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            new AgentError(null, true, "msg", Collections.emptyMap());
        });
    }

    @Test
    void constructor_nullContext_createsEmptyMap() {
         AgentError event = new AgentError(new RuntimeException("test"), false, "msg", null);
         assertNotNull(event.context());
         assertTrue(event.context().isEmpty());
    }
}

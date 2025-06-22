package com.skanga.workflow.persistence;

import com.skanga.workflow.WorkflowState;
import com.skanga.workflow.exception.WorkflowInterrupt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryWorkflowPersistenceTests {

    private InMemoryWorkflowPersistence persistence;
    private WorkflowInterrupt sampleInterrupt;
    private final String workflowId = "wf-test-123";

    @BeforeEach
    void setUp() {
        persistence = new InMemoryWorkflowPersistence();
        WorkflowState state = new WorkflowState(Map.of("key", "value"));
        sampleInterrupt = new WorkflowInterrupt(Map.of("saveData", "important"), "nodeA", state);
    }

    @Test
    void save_storesInterruptData() {
        persistence.save(workflowId, sampleInterrupt);
        assertEquals(1, persistence.size());
    }

    @Test
    void save_nullWorkflowId_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            persistence.save(null, sampleInterrupt);
        });
    }

    @Test
    void save_nullInterrupt_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            persistence.save(workflowId, null);
        });
    }

    @Test
    void load_existingId_returnsSavedData() {
        persistence.save(workflowId, sampleInterrupt);
        WorkflowInterrupt loadedInterrupt = persistence.load(workflowId);
        assertSame(sampleInterrupt, loadedInterrupt, "Should return the same saved interrupt instance.");
    }

    @Test
    void load_nonExistentId_returnsNull() {
        assertNull(persistence.load("non-existent-wf-id"));
    }

    @Test
    void load_nullWorkflowId_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            persistence.load(null);
        });
    }


    @Test
    void delete_existingId_removesData() {
        persistence.save(workflowId, sampleInterrupt);
        assertEquals(1, persistence.size());

        persistence.delete(workflowId);
        assertEquals(0, persistence.size());
        assertNull(persistence.load(workflowId), "Data should be null after deletion.");
    }

    @Test
    void delete_nonExistentId_doesNothing() {
        persistence.save(workflowId, sampleInterrupt); // Add some data
        assertEquals(1, persistence.size());

        assertDoesNotThrow(() -> persistence.delete("non-existent-wf-id"));
        assertEquals(1, persistence.size(), "Store size should not change for non-existent ID deletion.");
    }

    @Test
    void delete_nullWorkflowId_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            persistence.delete(null);
        });
    }


    @Test
    void clearAll_emptiesTheStore() {
        persistence.save("wf1", sampleInterrupt);
        WorkflowState state2 = new WorkflowState(Map.of("another", "data"));
        WorkflowInterrupt interrupt2 = new WorkflowInterrupt(Collections.emptyMap(), "nodeB", state2);
        persistence.save("wf2", interrupt2);
        assertEquals(2, persistence.size());

        persistence.clearAll();
        assertEquals(0, persistence.size());
        assertNull(persistence.load("wf1"));
        assertNull(persistence.load("wf2"));
    }

    @Test
    void size_reflectsNumberOfStoredItems() {
        assertEquals(0, persistence.size());
        persistence.save("id1", sampleInterrupt);
        assertEquals(1, persistence.size());
        persistence.save("id2", sampleInterrupt); // Using same interrupt object for different ID
        assertEquals(2, persistence.size());
        persistence.delete("id1");
        assertEquals(1, persistence.size());
    }

    // Concurrency test is complex for unit tests.
    // ConcurrentHashMap provides thread safety for map operations,
    // so basic save/load/delete from different threads should be safe.
    // A true stress test would be an integration or performance test.
}

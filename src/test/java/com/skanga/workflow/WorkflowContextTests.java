package com.skanga.workflow;

import com.skanga.workflow.exception.WorkflowInterrupt;
import com.skanga.workflow.persistence.InMemoryWorkflowPersistence;
import com.skanga.workflow.persistence.WorkflowPersistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito; // For mocking persistence if needed

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowContextTests {

    private String workflowId = "wf-123";
    private String nodeId = "node-abc";
    private WorkflowState initialState;
    private WorkflowPersistence mockPersistence;

    @BeforeEach
    void setUp() {
        initialState = new WorkflowState(Map.of("initialKey", "initialValue"));
        mockPersistence = Mockito.mock(WorkflowPersistence.class); // Use a mock for persistence
    }

    @Test
    void constructor_allArgs_initializesCorrectly() {
        Object feedback = "resume_feedback";
        WorkflowContext context = new WorkflowContext(workflowId, nodeId, initialState, mockPersistence, true, feedback);

        assertEquals(workflowId, context.getWorkflowId());
        assertEquals(nodeId, context.getCurrentNodeId());
        assertSame(initialState, context.getCurrentState());
        assertSame(mockPersistence, context.getPersistence());
        assertTrue(context.isResuming());
        assertEquals(feedback, context.getFeedbackForNode());
    }

    @Test
    void constructor_withoutResumeArgs_setsDefaults() {
        WorkflowContext context = new WorkflowContext(workflowId, nodeId, initialState, mockPersistence);
        assertFalse(context.isResuming());
        assertNull(context.getFeedbackForNode());
    }

    @Test
    void constructor_withoutPersistence_hasPersistenceReturnsFalse() {
        WorkflowContext context = new WorkflowContext(workflowId, nodeId, initialState, null, false, null); // No persistence
        assertFalse(context.hasPersistence());
        assertThrows(IllegalStateException.class, context::getPersistence, "getPersistence should throw if no persistence layer.");
    }

    @Test
    void constructor_nullChecks() {
        assertThrows(NullPointerException.class, () -> new WorkflowContext(null, nodeId, initialState, mockPersistence));
        assertThrows(NullPointerException.class, () -> new WorkflowContext(workflowId, null, initialState, mockPersistence));
        assertThrows(NullPointerException.class, () -> new WorkflowContext(workflowId, nodeId, null, mockPersistence));
    }


    @Test
    void interrupt_notResuming_throwsWorkflowInterrupt() {
        WorkflowContext context = new WorkflowContext(workflowId, nodeId, initialState, mockPersistence, false, null);
        Map<String, Object> dataToSave = Map.of("partialResult", "data");

        WorkflowInterrupt interrupt = assertThrows(WorkflowInterrupt.class, () -> {
            context.interrupt(dataToSave);
        });

        assertEquals(nodeId, interrupt.getCurrentNodeId());
        // Verify state in interrupt includes initial state AND dataToSave
        assertTrue(interrupt.getWorkflowState().containsKey("initialKey"));
        assertEquals("initialValue", interrupt.getWorkflowState().get("initialKey"));
        assertTrue(interrupt.getWorkflowState().containsKey("partialResult"));
        assertEquals("data", interrupt.getWorkflowState().get("partialResult"));

        assertEquals(dataToSave, interrupt.getDataToSave());
    }

    @Test
    void interrupt_dataToSaveIsNull_interruptStateIsCopyOfCurrent() {
        WorkflowState originalStateForInterrupt = new WorkflowState(Map.of("k", "v"));
        WorkflowContext context = new WorkflowContext(workflowId, nodeId, originalStateForInterrupt, mockPersistence, false, null);

        WorkflowInterrupt interrupt = assertThrows(WorkflowInterrupt.class, () -> {
            context.interrupt(null); // No extra data to save
        });

        assertEquals(originalStateForInterrupt.getAll(), interrupt.getWorkflowState().getAll(), "State in interrupt should be a copy of context's current state.");
        assertNotSame(originalStateForInterrupt, interrupt.getWorkflowState(), "State in interrupt should be a copy, not the same instance.");
        assertTrue(interrupt.getDataToSave().isEmpty());
    }


    @Test
    void interrupt_isResumingWithFeedback_returnsFeedbackAndClearsFlags() throws WorkflowInterrupt {
        String feedback = "This is the human feedback";
        WorkflowContext context = new WorkflowContext(workflowId, nodeId, initialState, mockPersistence, true, feedback);

        Object result = context.interrupt(null); // Try to interrupt, but should consume feedback

        assertSame(feedback, result);
        assertFalse(context.isResuming(), "isResuming should be false after feedback is consumed.");
        assertNull(context.getFeedbackForNode(), "feedbackForNode should be null after being consumed.");
    }

    @Test
    void interrupt_isResumingNoFeedback_throwsWorkflowInterrupt() {
        WorkflowContext context = new WorkflowContext(workflowId, nodeId, initialState, mockPersistence, true, null); // isResuming true, but no feedback

        assertThrows(WorkflowInterrupt.class, () -> {
            context.interrupt(null);
        });
    }

    @Test
    void getters_returnCorrectValues() {
        WorkflowState state = new WorkflowState(Map.of("testKey", "testValue"));
        WorkflowPersistence persistence = new InMemoryWorkflowPersistence(); // Use a real one for this
        Object feedback = new Object();

        WorkflowContext ctx = new WorkflowContext("wf1", "node1", state, persistence, true, feedback);
        assertEquals("wf1", ctx.getWorkflowId());
        assertEquals("node1", ctx.getCurrentNodeId());
        assertSame(state, ctx.getCurrentState());
        assertSame(persistence, ctx.getPersistence());
        assertTrue(ctx.hasPersistence());
        assertTrue(ctx.isResuming());
        assertSame(feedback, ctx.getFeedbackForNode());
    }

    @Test
    void packagePrivateSetters_updateContext() {
        // These setters are package-private, intended for Workflow engine use.
        WorkflowContext context = new WorkflowContext(workflowId, nodeId, initialState, mockPersistence);

        context.setCurrentNodeId("newNodeId");
        assertEquals("newNodeId", context.getCurrentNodeId());

        String newFeedback = "updated_feedback";
        context.setResumingState(true, newFeedback);
        assertTrue(context.isResuming());
        assertEquals(newFeedback, context.getFeedbackForNode());

        context.setResumingState(false, null);
        assertFalse(context.isResuming());
        assertNull(context.getFeedbackForNode());
    }

}

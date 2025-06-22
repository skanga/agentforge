package com.skanga.workflow.graph;

import com.skanga.workflow.WorkflowState;
import org.junit.jupiter.api.Test;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

class EdgeTests {

    private final String fromNode = "nodeA";
    private final String toNode = "nodeB";

    @Test
    void constructor_withIdsOnly_createsUnconditionalEdge() {
        Edge edge = new Edge(fromNode, toNode);
        assertEquals(fromNode, edge.getFromNodeId());
        assertEquals(toNode, edge.getToNodeId());
        assertNull(edge.getCondition(), "Condition should be null for unconditional edge.");
    }

    @Test
    void constructor_withIdsAndCondition_setsAllFields() {
        Predicate<WorkflowState> condition = state -> state.containsKey("key");
        Edge edge = new Edge(fromNode, toNode, condition);

        assertEquals(fromNode, edge.getFromNodeId());
        assertEquals(toNode, edge.getToNodeId());
        assertSame(condition, edge.getCondition());
    }

    @Test
    void constructor_nullFromNodeId_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new Edge(null, toNode));
    }

    @Test
    void constructor_nullToNodeId_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new Edge(fromNode, null));
    }


    @Test
    void shouldExecute_unconditionalEdge_returnsTrue() {
        Edge edge = new Edge(fromNode, toNode); // No condition
        WorkflowState state = new WorkflowState(); // State content doesn't matter here
        assertTrue(edge.shouldExecute(state));
    }

    @Test
    void shouldExecute_conditionalEdge_predicateReturnsTrue_returnsTrue() {
        Predicate<WorkflowState> conditionTrue = state -> true;
        Edge edge = new Edge(fromNode, toNode, conditionTrue);
        WorkflowState state = new WorkflowState();
        assertTrue(edge.shouldExecute(state));
    }

    @Test
    void shouldExecute_conditionalEdge_predicateReturnsFalse_returnsFalse() {
        Predicate<WorkflowState> conditionFalse = state -> false;
        Edge edge = new Edge(fromNode, toNode, conditionFalse);
        WorkflowState state = new WorkflowState();
        assertFalse(edge.shouldExecute(state));
    }

    @Test
    void shouldExecute_conditionalEdge_predicateUsesState_evaluatesCorrectly() {
        Predicate<WorkflowState> conditionChecksKey = state -> "expectedValue".equals(state.get("myKey"));
        Edge edge = new Edge(fromNode, toNode, conditionChecksKey);

        WorkflowState stateWithValue = new WorkflowState();
        stateWithValue.put("myKey", "expectedValue");
        assertTrue(edge.shouldExecute(stateWithValue));

        WorkflowState stateWithoutValue = new WorkflowState();
        stateWithoutValue.put("myKey", "otherValue");
        assertFalse(edge.shouldExecute(stateWithoutValue));

        WorkflowState stateMissingKey = new WorkflowState();
        assertFalse(edge.shouldExecute(stateMissingKey));
    }

    @Test
    void shouldExecute_conditionalEdge_nullState_throwsNullPointerException() {
        Predicate<WorkflowState> condition = state -> true; // Any condition
        Edge edge = new Edge(fromNode, toNode, condition);
        assertThrows(NullPointerException.class, () -> edge.shouldExecute(null));
    }

    @Test
    void toString_returnsMeaningfulRepresentation() {
        Edge edgeUnconditional = new Edge("start", "end");
        assertTrue(edgeUnconditional.toString().contains("from='start'"));
        assertTrue(edgeUnconditional.toString().contains("to='end'"));
        assertTrue(edgeUnconditional.toString().contains("hasCondition=false"));

        Edge edgeConditional = new Edge("start", "end", state -> true);
        assertTrue(edgeConditional.toString().contains("hasCondition=true"));
    }

    @Test
    void equalsAndHashCode_contract() {
        Predicate<WorkflowState> p1 = s -> true;
        Predicate<WorkflowState> p2 = s -> false; // Different predicate instance/logic

        Edge e1a = new Edge("A", "B", p1);
        Edge e1b = new Edge("A", "B", p1); // Same details
        Edge e2  = new Edge("A", "C", p1); // Different toNode
        Edge e3  = new Edge("X", "B", p1); // Different fromNode
        Edge e4  = new Edge("A", "B", p2); // Different condition
        Edge e5  = new Edge("A", "B", null); // No condition
        Edge e6  = new Edge("A", "B");      // Same as e5

        assertEquals(e1a, e1b);
        assertEquals(e1a.hashCode(), e1b.hashCode());

        assertEquals(e5, e6);
        assertEquals(e5.hashCode(), e6.hashCode());

        assertNotEquals(e1a, e2);
        assertNotEquals(e1a, e3);
        // Note: Predicate equality is reference equality for lambdas/most implementations.
        // So, e1a and e4 might be unequal even if their logic was identical but they are different instances.
        // This test assumes p1 and p2 are indeed different in a way that affects equality.
        // If p1 and p2 were identical lambdas, this might pass if compiler reuses, but don't rely on it.
        assertNotEquals(e1a, e4);
        assertNotEquals(e1a, e5);
    }
}

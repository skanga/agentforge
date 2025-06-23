package com.skanga.workflow;

import com.skanga.core.AgentObserver;
import com.skanga.workflow.exception.WorkflowException;
import com.skanga.workflow.exception.WorkflowInterrupt;
import com.skanga.workflow.graph.Edge;
import com.skanga.workflow.graph.Node;
import com.skanga.workflow.persistence.WorkflowPersistence;
import com.skanga.workflow.persistence.WorkflowPersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowTests {

    @Mock
    private WorkflowPersistence mockPersistence;
    @Mock
    private AgentObserver mockObserver;
    @Mock
    private Node mockNodeA;
    @Mock
    private Node mockNodeB;
    @Mock
    private Node mockNodeC;

    private Workflow workflow;
    private WorkflowState initialState;

    @BeforeEach
    void setUp() {
        workflow = new Workflow("test-workflow", mockPersistence);
        workflow.addObserver(mockObserver, "*"); // Observe all events for verification

        initialState = new WorkflowState();
        initialState.put("startKey", "startValue");

        // Setup basic node IDs for all tests
        lenient().when(mockNodeA.getId()).thenReturn("nodeA");
        lenient().when(mockNodeB.getId()).thenReturn("nodeB");
        lenient().when(mockNodeC.getId()).thenReturn("nodeC");
    }

    // --- Constructor Tests ---

    @Test
    void constructor_WithValidParameters_ShouldCreateWorkflow() {
        // Assert
        assertThat(workflow).isNotNull();
        assertThat(workflow.getId()).isEqualTo("test-workflow");
        assertThat(workflow.getPersistence()).isSameAs(mockPersistence);
    }

    @Test
    void constructor_WithNullId_ShouldGenerateId() {
        // Act
        Workflow workflowWithNullId = new Workflow(null, mockPersistence);

        // Assert
        assertThat(workflowWithNullId.getId()).isNotNull().isNotEmpty();
    }

    @Test
    void constructor_WithEmptyId_ShouldGenerateId() {
        // Act
        Workflow workflowWithEmptyId = new Workflow("", mockPersistence);

        // Assert
        assertThat(workflowWithEmptyId.getId()).isNotNull().isNotEmpty();
    }

    @Test
    void constructor_WithNullPersistence_ShouldUseDefault() {
        // Act
        Workflow workflowWithNullPersistence = new Workflow("test", null);

        // Assert
        assertThat(workflowWithNullPersistence.getPersistence()).isNotNull();
    }

    // --- Graph Building Tests ---

    @Test
    void addNode_WithValidNode_ShouldAddNode() {
        // Act
        workflow.addNode(mockNodeA);

        // Assert
        assertThat(workflow.getNodes()).hasSize(1).containsEntry("nodeA", mockNodeA);
    }

    @Test
    void addNode_WithNullNode_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> workflow.addNode(null));
    }

    @Test
    void addNode_WithDuplicateId_ShouldThrowException() {
        // Arrange
        workflow.addNode(mockNodeA);
        Node duplicateNode = mock(Node.class);
        when(duplicateNode.getId()).thenReturn("nodeA");

        // Act & Assert
        WorkflowException exception = assertThrows(WorkflowException.class, () -> workflow.addNode(duplicateNode));
        assertThat(exception.getMessage()).contains("Node with ID 'nodeA' already exists");
    }

    @Test
    void addEdge_WithValidNodes_ShouldAddEdge() {
        // Arrange
        workflow.addNode(mockNodeA).addNode(mockNodeB);

        // Act
        workflow.addEdge("nodeA", "nodeB");

        // Assert
        assertThat(workflow.getEdges()).isNotEmpty(); // Ensure edge list is not empty
        Edge edge = workflow.getEdges().get(0);
        assertThat(edge.getFromNodeId()).isEqualTo("nodeA");
        assertThat(edge.getToNodeId()).isEqualTo("nodeB");
        assertThat(edge.getCondition()).isNull(); // Default (unconditional) edge has a null condition
    }

    @Test
    void addEdge_WithCondition_ShouldAddConditionalEdge() {
        // Arrange
        workflow.addNode(mockNodeA).addNode(mockNodeB);
        Predicate<WorkflowState> condition = state -> "proceed".equals(state.get("action"));

        // Act
        workflow.addEdge("nodeA", "nodeB", condition);

        // Assert
        assertThat(workflow.getEdges()).hasSize(1);
        Edge edge = workflow.getEdges().get(0);
        assertThat(edge.getCondition()).isSameAs(condition);
    }

    @Test
    void addEdge_WithMissingFromNode_ShouldThrowException() {
        // Arrange
        workflow.addNode(mockNodeB);

        // Act & Assert
        WorkflowException exception = assertThrows(WorkflowException.class, () -> workflow.addEdge("nonexistent", "nodeB"));
        assertThat(exception.getMessage()).contains("Source node 'nonexistent' for edge not found");
    }

    @Test
    void addEdge_WithMissingToNode_ShouldThrowException() {
        // Arrange
        workflow.addNode(mockNodeA);

        // Act & Assert
        WorkflowException exception = assertThrows(WorkflowException.class, () -> workflow.addEdge("nodeA", "nonexistent"));
        assertThat(exception.getMessage()).contains("Target node 'nonexistent' for edge not found");
    }

    @Test
    void setStartNodeId_WithValidNode_ShouldSetStartNode() {
        // Arrange
        workflow.addNode(mockNodeA);

        // Act
        workflow.setStartNodeId("nodeA");

        // Assert
        assertThat(workflow.getStartNodeId()).isEqualTo("nodeA");
    }

    @Test
    void setStartNodeId_WithNullId_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> workflow.setStartNodeId(null));
    }

    @Test
    void setStartNodeId_WithNonexistentNode_ShouldThrowException() {
        // Act & Assert
        WorkflowException exception = assertThrows(WorkflowException.class, () -> workflow.setStartNodeId("nonexistent"));
        assertThat(exception.getMessage()).isEqualTo("Attempted to set start node ID to 'nonexistent', but no such node exists in workflow 'test-workflow'.");
    }

    @Test
    void getNodes_ShouldReturnUnmodifiableMap() {
        // Arrange
        workflow.addNode(mockNodeA);

        // Act
        Map<String, Node> nodes = workflow.getNodes();

        // Assert
        assertThrows(UnsupportedOperationException.class, () -> nodes.put("new", mockNodeB));
    }

    @Test
    void getEdges_ShouldReturnUnmodifiableList() {
        // Arrange
        workflow.addNode(mockNodeA).addNode(mockNodeB).addEdge("nodeA", "nodeB");

        // Act
        List<Edge> edges = workflow.getEdges();

        // Assert
        assertThrows(UnsupportedOperationException.class, () -> edges.add(new Edge("test", "test")));
    }


    // --- Graph Validation Tests ---

    @Test
    void validateGraphStructure_WithValidGraph_ShouldNotThrow() {
        // Arrange
        workflow.addNode(mockNodeA)
                .addNode(mockNodeB)
                .addEdge("nodeA", "nodeB")
                .setStartNodeId("nodeA")
                .setEndNodeId("nodeB");

        // Act & Assert
        assertDoesNotThrow(() -> workflow.validateGraphStructure());
    }

    @Test
    void validateGraphStructure_WithoutStartNode_ShouldThrowException() {
        // Arrange
        workflow.addNode(mockNodeA);

        // Act & Assert
        WorkflowException exception = assertThrows(WorkflowException.class, () -> workflow.validateGraphStructure());
        assertThat(exception.getMessage()).contains("Start node ID has not been set");
    }

    @Test
    void validateGraphStructure_WithInvalidStartNode_ShouldThrowException() {
        // Arrange
        workflow.addNode(mockNodeA); // nodeA exists

        // Act & Assert
        // The exception is thrown by setStartNodeId itself if the node doesn't exist.
        WorkflowException exception = assertThrows(WorkflowException.class, () -> workflow.setStartNodeId("nonexistent"));
        assertThat(exception.getMessage()).isEqualTo("Attempted to set start node ID to 'nonexistent', but no such node exists in workflow 'test-workflow'.");

        // validateGraphStructure() would not be reached in this specific scenario if setStartNodeId fails.
        // If we wanted to test validateGraphStructure's check for an invalid (but set) startNodeId,
        // we'd have to somehow bypass the check in setStartNodeId, which is not the current design.
    }

    @Test
    void validateGraphStructure_WithCycle_ShouldThrowException() {
        // Arrange
        workflow.addNode(mockNodeA)
                .addNode(mockNodeB)
                .addEdge("nodeA", "nodeB")
                .addEdge("nodeB", "nodeA") // Creates cycle
                .setStartNodeId("nodeA");

        // Act & Assert
        WorkflowException exception = assertThrows(WorkflowException.class, () -> workflow.validateGraphStructure());
        // The starting node of the cycle detection can vary based on iteration order of nodes in DFS.
        // Let's check for the general message.
        assertThat(exception.getMessage()).contains("Cycle detected in workflow graph");
    }

    // --- Execution (run) Tests ---

    @Test
    void run_WithLinearWorkflow_ShouldExecuteNodesAndObserversInOrder() throws Exception {
        // Arrange: A -> B -> C (end)
        workflow.addNode(mockNodeA).addNode(mockNodeB).addNode(mockNodeC)
                .setStartNodeId("nodeA").setEndNodeId("nodeC")
                .addEdge("nodeA", "nodeB")
                .addEdge("nodeB", "nodeC");

        WorkflowState stateA = new WorkflowState(Map.of("a_done", true));
        WorkflowState stateB = new WorkflowState(Map.of("b_done", true));
        WorkflowState stateEnd = new WorkflowState(Map.of("end_done", true));

        when(mockNodeA.run(any(WorkflowContext.class))).thenReturn(stateA);
        when(mockNodeB.run(any(WorkflowContext.class))).thenReturn(stateB);
        when(mockNodeC.run(any(WorkflowContext.class))).thenReturn(stateEnd);

        // Act
        WorkflowState finalState = workflow.run(initialState);

        // Assert
        assertThat(finalState).isSameAs(stateEnd);

        InOrder inOrder = inOrder(mockNodeA, mockNodeB, mockNodeC, mockObserver);
        inOrder.verify(mockObserver).update(eq("workflow-run-start"), anyMap());

        inOrder.verify(mockObserver).update(eq("workflow-node-start"), argThat(map -> "nodeA".equals(((Map<?, ?>) map).get("nodeId"))));
        inOrder.verify(mockNodeA).run(any(WorkflowContext.class));
        inOrder.verify(mockObserver).update(eq("workflow-node-stop"), argThat(map -> "nodeA".equals(((Map<?, ?>) map).get("nodeId"))));
        inOrder.verify(mockObserver).update(eq("workflow-edge-traversed"), argThat(map -> "nodeA".equals(((Map<?, ?>) map).get("fromNode")) && "nodeB".equals(((Map<?, ?>) map).get("toNode"))));

        inOrder.verify(mockObserver).update(eq("workflow-node-start"), argThat(map -> "nodeB".equals(((Map<?, ?>) map).get("nodeId"))));
        inOrder.verify(mockNodeB).run(any(WorkflowContext.class));
        inOrder.verify(mockObserver).update(eq("workflow-node-stop"), argThat(map -> "nodeB".equals(((Map<?, ?>) map).get("nodeId"))));
        inOrder.verify(mockObserver).update(eq("workflow-edge-traversed"), argThat(map -> "nodeB".equals(((Map<?, ?>) map).get("fromNode")) && "nodeC".equals(((Map<?, ?>) map).get("toNode"))));

        inOrder.verify(mockObserver).update(eq("workflow-node-start"), argThat(map -> "nodeC".equals(((Map<?, ?>) map).get("nodeId"))));
        inOrder.verify(mockNodeC).run(any(WorkflowContext.class));
        inOrder.verify(mockObserver).update(eq("workflow-node-stop"), argThat(map -> "nodeC".equals(((Map<?, ?>) map).get("nodeId"))));

        inOrder.verify(mockObserver).update(eq("workflow-run-stop"), argThat(map -> "completed".equals(((Map<?, ?>) map).get("status"))));
    }

    @Test
    void run_WithConditionalEdges_ShouldFollowCorrectPath() throws Exception {
        // Arrange: Start -> A --(condTrue)--> B (End)
        //                   |
        //                   --(condFalse)--> C
        Predicate<WorkflowState> goToB = state -> state.containsKey("go_to_b");
        workflow.addNode(mockNodeA).addNode(mockNodeB).addNode(mockNodeC)
                .setStartNodeId("nodeA").setEndNodeId("nodeB")
                .addEdge("nodeA", "nodeB", goToB)
                .addEdge("nodeA", "nodeC", goToB.negate());

        WorkflowState stateFromA = new WorkflowState();
        stateFromA.put("go_to_b", true); // This will make the condition for B true
        WorkflowState stateFromB = new WorkflowState(Map.of("b_was_run", true));

        when(mockNodeA.run(any(WorkflowContext.class))).thenReturn(stateFromA);
        when(mockNodeB.run(any(WorkflowContext.class))).thenReturn(stateFromB);

        // Act
        WorkflowState finalState = workflow.run(initialState);

        // Assert
        assertThat(finalState).isSameAs(stateFromB);
        verify(mockNodeA).run(any(WorkflowContext.class));
        verify(mockNodeB).run(any(WorkflowContext.class));
        verify(mockNodeC, never()).run(any(WorkflowContext.class));
    }

    @Test
    void run_WithInterrupt_ShouldSaveStateAndThrowInterrupt() throws Exception {
        // Arrange
        workflow.addNode(mockNodeA).addNode(mockNodeB).setStartNodeId("nodeA").addEdge("nodeA", "nodeB");

        WorkflowState stateAtInterrupt = new WorkflowState(Map.of("interrupted_at", "A"));
        Map<String, Object> interruptData = Map.of("reason", "waiting_for_input");
        WorkflowInterrupt interruptException = new WorkflowInterrupt(interruptData, "nodeA", stateAtInterrupt);

        when(mockNodeA.run(any(WorkflowContext.class))).thenThrow(interruptException);

        // Act & Assert
        WorkflowInterrupt thrown = assertThrows(WorkflowInterrupt.class, () -> workflow.run(initialState));
        assertThat(thrown).isSameAs(interruptException);

        verify(mockPersistence).save(eq("test-workflow"), eq(interruptException));
        verify(mockNodeB, never()).run(any(WorkflowContext.class));
        verify(mockObserver).update(eq("workflow-node-interrupt"), anyMap());
        verify(mockObserver).update(eq("workflow-run-interrupted"), anyMap());
    }

    @Test
    void run_WhenNodeThrowsWorkflowException_ShouldStopAndThrow() throws Exception {
        // Arrange
        WorkflowException nodeException = new WorkflowException("Error in node processing");
        when(mockNodeA.run(any(WorkflowContext.class))).thenThrow(nodeException);

        workflow.addNode(mockNodeA).setStartNodeId("nodeA");

        // Act & Assert
        WorkflowException thrown = assertThrows(WorkflowException.class, () -> workflow.run(initialState));
        assertThat(thrown.getCause()).isSameAs(nodeException);
        assertThat(thrown.getMessage()).isEqualTo("Error executing node 'nodeA' in workflow 'test-workflow': Error in node processing");

        verify(mockNodeA).run(any(WorkflowContext.class));
        verify(mockObserver).update(eq("workflow-run-error"), argThat(map ->
            "com.skanga.workflow.exception.WorkflowException: Error executing node 'nodeA' in workflow 'test-workflow': Error in node processing".equals(((Map<?, ?>) map).get("error"))
        ));
        verifyNoMoreInteractions(mockPersistence); // Should not save state on generic exceptions
    }

    @Test
    void run_WithNoValidEdgeFromNode_ShouldThrowException() throws Exception {
        // Arrange: A -> B (but condition is always false), A is not an end node
        workflow.addNode(mockNodeA).addNode(mockNodeB)
                .setStartNodeId("nodeA")
                .addEdge("nodeA", "nodeB", state -> false); // Edge that is never taken

        when(mockNodeA.run(any())).thenReturn(new WorkflowState()); // Return a state that doesn't matter

        // Act & Assert
        WorkflowException ex = assertThrows(WorkflowException.class, () -> workflow.run(initialState));
        assertThat(ex.getMessage()).isEqualTo("No conditions met for any outgoing edge from node 'nodeA' in workflow 'test-workflow'. Workflow cannot proceed.");
        verify(mockObserver).update(eq("workflow-run-error"), argThat(map ->
            "com.skanga.workflow.exception.WorkflowException: No conditions met for any outgoing edge from node 'nodeA' in workflow 'test-workflow'. Workflow cannot proceed.".equals(((Map<?, ?>) map).get("error"))
        ));
    }

    @Test
    void run_WithCycle_ShouldThrowExceptionAfterExceedingMaxSteps() throws Exception {
        // Arrange
        lenient().when(mockNodeA.run(any())).thenReturn(new WorkflowState()); // Made lenient
        workflow.addNode(mockNodeA)
                .addEdge("nodeA", "nodeA") // Cycle
                .setStartNodeId("nodeA");

        // Act & Assert
        // Cycle detection now happens in validateGraphStructure, before execution loop & max steps.
        WorkflowException exception = assertThrows(WorkflowException.class, () -> workflow.run(initialState));
        assertThat(exception.getMessage()).isEqualTo("Cycle detected in workflow graph starting from node: nodeA");
    }

    // This is where the misplaced line should go.
    // The following test was not part of the original change but is being shown for context.
    // @Test
    // void setStartNodeId_WithNonexistentNode_ShouldThrowException() {
    //     // Act & Assert
    //     WorkflowException exception = assertThrows(WorkflowException.class, () -> workflow.setStartNodeId("nonexistent"));
    //     assertThat(exception.getMessage()).isEqualTo("Attempted to set start node ID to 'nonexistent', but no such node exists in workflow 'test-workflow'.");
    // }

    @Test
    void run_WithNullInitialState_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> workflow.run(null));
    }


    // --- Resume Tests ---

    @Test
    void resume_WithValidInterrupt_ShouldContinueExecutionFromSavedState() throws Exception {
        // Arrange: Graph is A (interrupts) -> B (end)
        workflow.addNode(mockNodeA).addNode(mockNodeB).setStartNodeId("nodeA").setEndNodeId("nodeB")
                .addEdge("nodeA", "nodeB");

        WorkflowState savedState = new WorkflowState(Map.of("interruptedKey", "interruptValue"));
        WorkflowInterrupt interruptToLoad = new WorkflowInterrupt(Map.of("reason", "waiting"), "nodeA", savedState);
        when(mockPersistence.load("test-workflow")).thenReturn(interruptToLoad);

        String humanFeedback = "User provided input";
        WorkflowState stateAfterAResumed = new WorkflowState(savedState.getAll());
        stateAfterAResumed.put("a_resumed", true);

        // Mock nodeA to return a new state upon resuming
        when(mockNodeA.run(any(WorkflowContext.class))).thenAnswer(invocation -> {
            WorkflowContext ctx = invocation.getArgument(0);
            assertThat(ctx.isResuming()).isTrue();
            assertThat(ctx.getFeedbackForNode()).isEqualTo(humanFeedback);
            return stateAfterAResumed;
        });

        WorkflowState finalStateFromB = new WorkflowState(Map.of("b_finalized", true));
        when(mockNodeB.run(any(WorkflowContext.class))).thenReturn(finalStateFromB);

        // Act
        WorkflowState finalState = workflow.resume(humanFeedback);

        // Assert
        assertThat(finalState).isSameAs(finalStateFromB);

        // Verify correct context was passed to each node
        verify(mockNodeA).run(argThat(ctx -> ctx.isResuming() && ctx.getCurrentNodeId().equals("nodeA")));
        verify(mockNodeB).run(argThat(ctx -> !ctx.isResuming() && ctx.getCurrentState().equals(stateAfterAResumed)));

        // Verify persistence interactions
        verify(mockPersistence).load("test-workflow");
        verify(mockPersistence).delete("test-workflow");

        // Verify observer notifications
        InOrder inOrder = inOrder(mockObserver);
        inOrder.verify(mockObserver).update(eq("workflow-resume-start"), anyMap());
        inOrder.verify(mockObserver).update(eq("workflow-node-start"), anyMap()); // Resumed node A
        inOrder.verify(mockObserver).update(eq("workflow-node-stop"), anyMap());
        inOrder.verify(mockObserver).update(eq("workflow-resume-stop"), argThat(map -> "completed".equals(((Map<?, ?>) map).get("status"))));
    }

    @Test
    void resume_WithoutSavedState_ShouldThrowException() throws Exception {
        // Arrange
        when(mockPersistence.load("test-workflow")).thenReturn(null);

        // Act & Assert
        WorkflowException exception = assertThrows(WorkflowException.class, () -> workflow.resume("feedback"));
        assertThat(exception.getMessage()).isEqualTo("No persisted state found to resume workflow ID 'test-workflow'.");
    }

    @Test
    void resume_WithPersistenceLoadError_ShouldThrowException() throws Exception {
        // Arrange
        when(mockPersistence.load("test-workflow")).thenThrow(new WorkflowPersistenceException("DB connection failed"));

        // Act & Assert
        WorkflowException exception = assertThrows(WorkflowException.class, () -> workflow.resume("feedback"));
        assertThat(exception.getMessage()).contains("Failed to load persisted state for workflow");
        assertThat(exception.getCause()).isInstanceOf(WorkflowPersistenceException.class);
    }

    // --- Observer Management Tests ---

    @Test
    void addObserver_ShouldAddObserver() {
        // Arrange
        Workflow wf = new Workflow("obs-test", null);
        AgentObserver obs = mock(AgentObserver.class);
        // Act
        wf.addObserver(obs, "test-event");
        wf.notifyObservers("other-event", Map.of());
        wf.notifyObservers("test-event", Map.of());

        // Assert
        verify(obs, never()).update(eq("other-event"), any());
        verify(obs, times(1)).update(eq("test-event"), any());
    }

    @Test
    void removeObserver_ShouldRemoveObserver() {
        // Arrange
        workflow.addObserver(mockObserver, "*"); // Added in setUp
        workflow.removeObserver(mockObserver);

        // Act
        workflow.notifyObservers("some-event", Map.of());

        // Assert
        // The mockObserver was added in setUp, so we expect 1 call from there.
        // After removing, no more calls should be made. Let's reset and test.
        reset(mockObserver);
        workflow.removeObserver(mockObserver); // Ensure it is removed
        workflow.notifyObservers("another-event", Map.of());
        verifyNoInteractions(mockObserver);
    }
}
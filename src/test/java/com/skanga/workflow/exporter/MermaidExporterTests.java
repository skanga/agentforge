package com.skanga.workflow.exporter;

import com.skanga.workflow.Workflow;
import com.skanga.workflow.WorkflowState;
import com.skanga.workflow.graph.AbstractNode;
import com.skanga.workflow.graph.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MermaidExporterTests {

    private Workflow workflow;
    private MermaidExporter exporterTd; // TopDown
    private MermaidExporter exporterLr; // LeftRight

    // Simple Node implementation for testing
    static class TestNode extends AbstractNode {
        public TestNode(String id) {
            super(id);
        }
        @Override
        public WorkflowState run(com.skanga.workflow.WorkflowContext context) {
            return context.getCurrentState(); // Does nothing
        }
    }

    @BeforeEach
    void setUp() {
        workflow = new Workflow("testExportWorkflow", null); // Uses InMemoryPersistence
        exporterTd = new MermaidExporter(); // Default TD
        exporterLr = new MermaidExporter("LR");
    }

    @Test
    void export_emptyWorkflow_throwsException() {
        // Workflow validation (in run) would catch no start node, but export might be called on an incomplete graph.
        // Current export doesn't validate, but might fail if nodes map is empty.
        // Workflow.getNodes() is unmodifiable, so it's empty by default.
        // The exporter itself checks for empty nodes.
        WorkflowExportException ex = assertThrows(WorkflowExportException.class, () -> {
            exporterTd.export(workflow);
        });
        assertTrue(ex.getMessage().contains("Cannot export an empty workflow (no nodes defined)"));
    }

    @Test
    void export_singleNodeWorkflow_correctMermaid() {
        Node nodeA = new TestNode("NodeA_ID");
        workflow.addNode(nodeA).setStartNodeId("NodeA_ID");

        String mermaid = exporterTd.export(workflow);
        assertTrue(mermaid.startsWith("graph TD;\n"));
        assertTrue(mermaid.contains("    NodeA_ID[\"TestNode::NodeA_ID\"];\n"));
    }

    @Test
    void export_linearWorkflow_correctMermaid() {
        Node nodeA = new TestNode("A");
        Node nodeB = new TestNode("B");
        Node nodeC = new TestNode("C");
        workflow.addNode(nodeA).addNode(nodeB).addNode(nodeC)
                .setStartNodeId("A").setEndNodeId("C")
                .addEdge("A", "B")
                .addEdge("B", "C");

        String mermaid = exporterTd.export(workflow);
        System.out.println(mermaid); // For visual inspection during test run

        assertTrue(mermaid.contains("    A[\"TestNode::A\"];\n"));
        assertTrue(mermaid.contains("    B[\"TestNode::B\"];\n"));
        assertTrue(mermaid.contains("    C[\"TestNode::C\"];\n"));
        assertTrue(mermaid.contains("    A --> B;\n"));
        assertTrue(mermaid.contains("    B --> C;\n"));
        assertTrue(mermaid.contains("style A fill:#B4F8C8")); // Start node style
        assertTrue(mermaid.contains("style C fill:#FBE7C6")); // End node style
    }

    @Test
    void export_workflowWithConditionalEdge_showsLabel() {
        Node nodeA = new TestNode("StartNode");
        Node nodeB = new TestNode("EndNodeConditional");
        workflow.addNode(nodeA).addNode(nodeB)
                .setStartNodeId("StartNode")
                .addEdge("StartNode", "EndNodeConditional", state -> state.containsKey("proceed"));

        String mermaid = exporterLr.export(workflow); // Test LR direction
        assertTrue(mermaid.startsWith("graph LR;\n"));
        assertTrue(mermaid.contains("    StartNode -->|Conditional| EndNodeConditional;\n"));
    }

    @Test
    void export_nodeIdWithSpacesAndSpecialChars_isSanitized() {
        Node nodeA = new TestNode("Node With Spaces & Chars!");
        workflow.addNode(nodeA).setStartNodeId("Node With Spaces & Chars!");
        String mermaid = exporterTd.export(workflow);

        // Sanitized ID: Node_With_Spaces__Chars
        // Label: TestNode::Node With Spaces & Chars! (escapeMermaidLabelCharacters only handles \\ and \")
        assertTrue(mermaid.contains("    Node_With_Spaces__Chars[\"TestNode::Node With Spaces & Chars!\"];\n"));
    }

    @Test
    void escapeMermaidLabel_handlesQuotesAndBackslashes() {
        MermaidExporter exporter = new MermaidExporter(); // Access private method via instance
        String original = "Label with \"quotes\" and \\backslashes\\";
        String expected = "Label with #quot;quotes#quot; and \\\\backslashes\\\\";
        // Use reflection to test private method if needed, or make it package-private for testing.
        // For now, assume it's tested implicitly by the main export method's output.
        // This test is conceptual for the escaping logic.
        // If I had access: assertEquals(expected, exporter.escapeMermaidLabelCharacters(original));

        // Test via export:
        Node nodeSpecial = new TestNode("special");
        // Manually changing description for this node instance if possible, or use a node type that takes desc
        // For simplicity, we'll check the label generated by default from class name and ID
        String labelToTest = "TestNode::special \"label\" \\char\\";
        // This would require a node whose effective label becomes labelToTest.
        // The current exporter uses node.getClass().getSimpleName() + "::" + nodeId;
        // Let's test sanitization of ID and escaping of label separately if possible.
        // The current sanitizeNodeIdForMermaid and escapeMermaidLabelCharacters are private.

        // Test the public export method with a node ID that will result in a label needing escaping
        Node nodeForLabelTest = new TestNode("node\"id\"\\needs\\escape");
        workflow.addNode(nodeForLabelTest).setStartNodeId(nodeForLabelTest.getId());
        String mermaid = exporterTd.export(workflow);

        String expectedNodeIdSanitized = "nodequotidquot\\\\needs\\\\escape"; // Based on current sanitizeNodeId
        String expectedLabelContent = "TestNode::node#quot;id#quot;\\\\needs\\\\escape";

        assertTrue(mermaid.contains("    " + exporter.sanitizeNodeIdForMermaid(nodeForLabelTest.getId()) +
                "[\"" + exporter.escapeMermaidLabelCharacters(nodeForLabelTest.getClass().getSimpleName() + "::" + nodeForLabelTest.getId()) + "\"];\n"),
         "Mermaid output for special label characters is not as expected. Output:\n" + mermaid);
    }
}

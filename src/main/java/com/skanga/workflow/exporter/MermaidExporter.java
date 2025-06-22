package com.skanga.workflow.exporter;

import com.skanga.workflow.Workflow;
import com.skanga.workflow.graph.Edge;
import com.skanga.workflow.graph.Node;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern; // For sanitizing node IDs

/**
 * A {@link WorkflowExporter} that converts a {@link Workflow} into the
 * <a href="https://mermaid.js.org/syntax/flowchart.html">Mermaid flowchart syntax</a>.
 * This allows for easy visualization of the workflow structure.
 *
 * <p><b>Generated Output Example:</b></p>
 * <pre>{@code
 * graph TD; // or LR, etc.
 *   node1_id["NodeClass::node1_id"];
 *   node2_id["NodeClass::node2_id"];
 *   node1_id --> node2_id;
 *   node1_id -->|Conditional| node3_id;
 * }</pre>
 *
 * <p>Node IDs are sanitized to be Mermaid-compatible. Node labels currently include
 * the node's class simple name and its ID. Edge labels indicate if an edge is conditional.</p>
 */
public class MermaidExporter implements WorkflowExporter {

    /** The direction of the Mermaid graph (e.g., "TD" for TopDown, "LR" for LeftRight). */
    private final String direction;

    /**
     * Pattern for characters that are generally problematic in Mermaid node IDs if not part of a quoted label.
     * This includes spaces, semicolons, commas, colons. We replace them with underscores.
     * Additionally, remove any characters not alphanumeric or underscore/hyphen.
     */
    private static final Pattern MERMAID_ID_SANITIZE_PATTERN = Pattern.compile("[\\s;:,]");
    private static final Pattern MERMAID_ID_CLEANUP_PATTERN = Pattern.compile("[^a-zA-Z0-9_\\-]");


    /**
     * Constructs a MermaidExporter with a specified graph direction.
     * @param direction The direction for the Mermaid graph (e.g., "TD", "LR").
     *                  If null or empty, defaults to "TD" (Top-Down).
     */
    public MermaidExporter(String direction) {
        this.direction = (direction == null || direction.trim().isEmpty()) ? "TD" : direction.trim().toUpperCase();
    }

    /**
     * Constructs a MermaidExporter with the default graph direction "TD" (Top-Down).
     */
    public MermaidExporter() {
        this("TD");
    }

    /**
     * {@inheritDoc}
     * <p>Generates a Mermaid flowchart syntax string representing the workflow's nodes and edges.</p>
     * @throws NullPointerException if the workflow is null.
     * @throws WorkflowExportException if the workflow is missing critical information like nodes.
     */
    @Override
    public String export(Workflow workflow) throws WorkflowExportException {
        Objects.requireNonNull(workflow, "Workflow to export cannot be null.");
        if (workflow.getNodes().isEmpty()) {
            throw new WorkflowExportException("Cannot export an empty workflow (no nodes defined) with ID: " + workflow.getId());
        }

        StringBuilder mermaidGraph = new StringBuilder();
        mermaidGraph.append("graph ").append(direction).append(";\n\n"); // Start graph definition

        // Add node definitions: id["label"]
        for (Map.Entry<String, Node> entry : workflow.getNodes().entrySet()) {
            String nodeId = entry.getKey();
            Node node = entry.getValue();
            // Using node's class simple name and its ID for the label.
            String nodeLabel = node.getClass().getSimpleName() + "::" + nodeId;
            mermaidGraph.append("    ")
                        .append(sanitizeNodeIdForMermaid(nodeId))
                        .append("[\"")
                        .append(escapeMermaidLabelCharacters(nodeLabel))
                        .append("\"];\n");
        }
        mermaidGraph.append("\n");

        // Add edge definitions: from --> to or from -->|label| to
        for (Edge edge : workflow.getEdges()) {
            String fromNodeSanitized = sanitizeNodeIdForMermaid(edge.getFromNodeId());
            String toNodeSanitized = sanitizeNodeIdForMermaid(edge.getToNodeId());
            String edgeLabel = "";

            if (edge.getCondition() != null) {
                // Representing a Predicate condition as a string is complex.
                // Simply labeling it "Conditional" or using a hash/ID of the predicate.
                // A more advanced system might allow edges to have explicit names/descriptions.
                edgeLabel = "Conditional"; // Basic label for conditional edges
            }

            mermaidGraph.append("    ").append(fromNodeSanitized);
            if (!edgeLabel.isEmpty()) {
                mermaidGraph.append(" -->|")
                            .append(escapeMermaidLabelCharacters(edgeLabel))
                            .append("| ");
            } else {
                mermaidGraph.append(" --> ");
            }
            mermaidGraph.append(toNodeSanitized).append(";\n");
        }

        // Optional: Add styling for start and end nodes
        if (workflow.getStartNodeId() != null && !workflow.getStartNodeId().isEmpty()) {
            mermaidGraph.append("\n    style ")
                        .append(sanitizeNodeIdForMermaid(workflow.getStartNodeId()))
                        .append(" fill:#B4F8C8,stroke:#000,stroke-width:2px,color:#000;\n"); // Light green fill for start
        }
        if (workflow.getEndNodeId() != null && !workflow.getEndNodeId().isEmpty() && workflow.getNodes().containsKey(workflow.getEndNodeId())) {
             mermaidGraph.append("    style ")
                        .append(sanitizeNodeIdForMermaid(workflow.getEndNodeId()))
                        .append(" fill:#FBE7C6,stroke:#000,stroke-width:2px,color:#000;\n"); // Light orange/peach for end
        }


        return mermaidGraph.toString();
    }

    /**
     * Sanitizes a node ID to be compliant with Mermaid flowchart ID syntax.
     * Mermaid IDs should generally be alphanumeric and not contain spaces or special characters
     * that could break the syntax, unless the ID itself is quoted (which we are not doing for IDs, only labels).
     * This replaces common problematic characters with underscores and removes others.
     *
     * @param nodeId The original node ID.
     * @return A sanitized version of the node ID.
     */
    String sanitizeNodeIdForMermaid(String nodeId) {
        if (nodeId == null) return "_null_id_";
        String sanitized = MERMAID_ID_SANITIZE_PATTERN.matcher(nodeId).replaceAll("_");
        sanitized = MERMAID_ID_CLEANUP_PATTERN.matcher(sanitized).replaceAll("");
        // Ensure ID is not empty after sanitization and doesn't start/end with underscore if that's an issue
        if (sanitized.isEmpty()) return "_empty_id_";
        return sanitized;
    }

    /**
     * Escapes characters within a Mermaid label string to prevent breaking the label's quotes.
     * Mermaid labels are typically enclosed in double quotes {@code "label_text"}.
     * Any double quotes within the label text itself must be escaped.
     *
     * @param label The raw label text.
     * @return The label text with characters escaped for safe inclusion in a Mermaid definition.
     */
    String escapeMermaidLabelCharacters(String label) {
        if (label == null) return "";
        // For Mermaid, double quotes in labels are problematic. Replace with HTML entity #quot;
        // Backslashes should also be escaped for general string safety.
        return label.replace("\\", "\\\\").replace("\"", "#quot;");
    }
}

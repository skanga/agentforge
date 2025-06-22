package com.skanga.workflow.exporter;

import com.skanga.workflow.Workflow;

/**
 * Interface for workflow exporters.
 * Implementations of this interface are responsible for converting a {@link Workflow}
 * object (representing its structure of nodes and edges) into a specific string-based
 * representation or format.
 *
 * <p>Examples of export formats could include:
 * <ul>
 *   <li>Diagramming languages (e.g., Mermaid, PlantUML, DOT).</li>
 *   <li>Data serialization formats (e.g., JSON, XML, YAML representing the graph structure).</li>
 *   <li>Custom textual descriptions.</li>
 * </ul>
 * </p>
 */
public interface WorkflowExporter {

    /**
     * Exports the given {@link Workflow} into a string representation according to the
     * exporter's specific format.
     *
     * @param workflow The {@link Workflow} instance to export. Must not be null.
     * @return A string representing the workflow in the target format.
     * @throws WorkflowExportException if an error occurs during the export process
     *                                 (e.g., issues with formatting, invalid workflow structure
     *                                 that prevents export, I/O errors if writing to a stream implicitly).
     */
    String export(Workflow workflow) throws WorkflowExportException;
}

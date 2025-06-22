package com.skanga.tools.toolkits;

import com.skanga.tools.Tool;
import java.util.List;

/**
 * Represents a collection or suite of {@link Tool} instances.
 * A toolkit provides a way to group related tools and can offer
 * optional guidelines on how an AI model should utilize these tools effectively.
 *
 * <p>The concept of a Toolkit in PHP was also a direct container of tools.
 * In this Java version, `Toolkit` is an interface, and {@link AbstractToolkitBase}
 * provides a base implementation that can dynamically provide or filter tools.</p>
 */
public interface Toolkit {

    /**
     * Provides optional guidelines for an AI model on how to best use the tools
     * contained within this toolkit. This could be a natural language string
     * included in a system prompt or as part of the tool descriptions.
     *
     * <p>Example: "Use the weather tool for current conditions, and the forecast tool
     * for future predictions. Always specify the location."</p>
     *
     * @return A string containing guidelines, or null if no specific guidelines are provided.
     */
    String getGuidelines();

    /**
     * Gets the list of {@link Tool} instances available in this toolkit.
     * The actual list of tools returned might be subject to filtering or dynamic
     * provision by the implementing class (e.g., based on exclusions set in
     * {@link AbstractToolkitBase}).
     *
     * @return A list of {@link Tool} instances. Should not be null, can be empty.
     */
    List<Tool> getTools();
}

package com.skanga.tools.toolkits;

import com.skanga.tools.Tool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects; // Added import
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream; // Added import

/**
 * An abstract base class for implementing {@link Toolkit}.
 * It provides functionality for managing a list of tools and excluding specific tools
 * by their class names.
 *
 * <p>Concrete toolkits should extend this class and implement the
 * {@link #provideTools()} method to supply the initial list of all tools
 * they can offer. The {@link #getTools()} method will then handle filtering
 * based on any exclusions.</p>
 *
 * <p>This differs slightly from the PHP `Toolkit` which acted more like a direct
 * container (ArrayObject). This Java version uses an abstract method `provideTools()`
 * for subclasses to define their toolset, promoting more dynamic or configured tool loading.</p>
 */
public abstract class AbstractToolkitBase implements Toolkit {

    /**
     * A set of fully qualified class names of {@link Tool} implementations
     * that should be excluded from the list returned by {@link #getTools()}.
     */
    protected Set<String> excludedToolClasses = new HashSet<>();

    /**
     * Excludes specified tool classes from being returned by {@link #getTools()}.
     * Tools are identified by their fully qualified class names.
     *
     * @param toolClassNames A list of fully qualified class names of the {@link Tool}
     *                       implementations to exclude. If null, no changes are made.
     */
    public void exclude(List<String> toolClassNames) {
        if (toolClassNames != null) {
            this.excludedToolClasses.addAll(toolClassNames);
        }
    }

    /**
     * Clears all currently configured tool class exclusions.
     * After calling this, {@link #getTools()} will return all tools from {@link #provideTools()}
     * without filtering by class name.
     */
    public void clearExclusions() {
        this.excludedToolClasses.clear();
    }

    /**
     * Abstract method to be implemented by concrete toolkit subclasses.
     * This method should return the complete, unfiltered list of all {@link Tool} instances
     * that this toolkit can provide. The {@link #getTools()} method will then
     * apply any configured exclusions to this list.
     *
     * @return A list of all {@link Tool} instances potentially available from this toolkit.
     *         Can be an empty list if the toolkit is empty or dynamically provides no tools.
     */
    public abstract List<Tool> provideTools();

    /**
     * {@inheritDoc}
     * <p>This implementation retrieves all tools via {@link #provideTools()} and then
     * filters out any tools whose class names are present in the {@code excludedToolClasses} set.</p>
     */
    @Override
    public List<Tool> getTools() {
        List<Tool> allTools = provideTools();
        if (allTools == null) {
            return Collections.emptyList();
        }

        // Always filter out null tools first
        Stream<Tool> stream = allTools.stream().filter(Objects::nonNull);

        // Then apply exclusions if any
        if (!excludedToolClasses.isEmpty()) {
            stream = stream.filter(tool -> !excludedToolClasses.contains(tool.getClass().getName()));
        }

        return stream.collect(Collectors.toUnmodifiableList());
    }

    /**
     * {@inheritDoc}
     * <p>This base implementation returns {@code null}, indicating no specific guidelines
     * are provided by default. Subclasses should override this method to provide
     * relevant usage guidelines for their set of tools.</p>
     */
    @Override
    public String getGuidelines() {
        return null;
    }
}

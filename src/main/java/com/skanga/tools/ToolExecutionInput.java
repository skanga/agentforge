package com.skanga.tools;

import java.util.Collections;
import java.util.HashMap; // For creating a defensive copy
import java.util.Map;

/**
 * Represents the input arguments provided for a tool's execution.
 * This record is a wrapper around a map of argument names to their values.
 * It promotes immutability by making a defensive copy of the provided map.
 *
 * @param arguments A map where keys are argument names (String) and values are their
 *                  corresponding argument values (Object). The map passed to the constructor
 *                  is defensively copied and made unmodifiable.
 */
public record ToolExecutionInput(Map<String, Object> arguments) {
    /**
     * Canonical constructor for ToolExecutionInput.
     * Ensures the arguments map is unmodifiable and a defensive copy is made.
     * If the provided map is null, an empty unmodifiable map is used.
     *
     * @param arguments The map of argument names to values.
     */
    public ToolExecutionInput {
        // Ensure arguments map is unmodifiable to maintain immutability
        // and make a defensive copy.
        arguments = (arguments == null) ?
                    Collections.emptyMap() :
                    Collections.unmodifiableMap(new HashMap<>(arguments));
    }

    /**
     * Convenience method to get an argument's value by its name.
     *
     * @param name The name of the argument to retrieve.
     * @return The value of the argument, or null if the argument with the given name is not found.
     */
    public Object getArgument(String name) {
        return arguments.get(name);
    }

    /**
     * Convenience method to get an argument's value by its name, with a default value
     * if the argument is not found.
     *
     * @param name         The name of the argument to retrieve.
     * @param defaultValue The value to return if the argument with the given name is not found.
     * @param <T>          The type of the argument value.
     * @return The argument's value, or the `defaultValue` if not found.
     *         The caller is responsible for ensuring the type matches `T`.
     */
    @SuppressWarnings("unchecked") // Caller is responsible for type T matching actual value or default
    public <T> T getArgumentOrDefault(String name, T defaultValue) {
        return (T) arguments.getOrDefault(name, defaultValue);
    }
}

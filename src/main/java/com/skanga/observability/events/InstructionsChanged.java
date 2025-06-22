package com.skanga.observability.events;

import java.util.Collections;
import java.util.Map;
// No specific Objects.requireNonNull for String fields if null is a valid state (e.g. initial instructions)

/**
 * Event data for when an agent's system instructions are modified.
 * This is important for tracking how the agent's guiding directives change over time,
 * for example, due to RAG context updates or explicit programmatic changes.
 *
 * @param oldInstructions The previous system instructions. Can be null if no instructions were set before,
 *                        or if this represents the initial setting of instructions.
 * @param newInstructions The new system instructions being applied. Can be null if instructions are being cleared.
 * @param context         Optional map providing context for why the instructions changed
 *                        (e.g., {"reason": "RAG context update"}, {"source": "user_override"}).
 *                        Can be null or empty. A defensive copy is made.
 */
public record InstructionsChanged(
    String oldInstructions,
    String newInstructions,
    Map<String, Object> context
) {
    /**
     * Canonical constructor for InstructionsChanged.
     * Makes the context map unmodifiable.
     */
    public InstructionsChanged {
        context = (context != null) ? Collections.unmodifiableMap(context) : Collections.emptyMap();
    }
    /**
     * Convenience constructor when no specific context is provided for the change.
     * @param oldInstructions The previous system instructions.
     * @param newInstructions The new system instructions.
     */
    public InstructionsChanged(String oldInstructions, String newInstructions) {
        this(oldInstructions, newInstructions, null);
    }
}

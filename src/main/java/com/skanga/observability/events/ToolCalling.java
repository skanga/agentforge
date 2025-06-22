package com.skanga.observability.events;

import com.skanga.core.messages.ToolCallMessage;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Event data for when an agent is about to execute one or more tools.
 * This event is typically triggered after an AI model's response indicates
 * that tools should be called (i.e., the AI has generated a {@link ToolCallMessage}).
 *
 * @param toolCallMessage The {@link ToolCallMessage} received from the LLM,
 *                        containing the details of the specific tool(s) and arguments
 *                        requested by the AI. Must not be null.
 * @param agentContext    Optional map of key-value pairs providing context about the
 *                        agent's state or other relevant information at the time of tool calling.
 *                        Can be null or empty. A defensive copy is made.
 */
public record ToolCalling(
    ToolCallMessage toolCallMessage,
    Map<String, Object> agentContext
) {
    /**
     * Canonical constructor for ToolCalling.
     * Ensures toolCallMessage is not null and makes agentContext unmodifiable.
     */
    public ToolCalling {
        Objects.requireNonNull(toolCallMessage, "toolCallMessage cannot be null for ToolCalling event.");
        agentContext = (agentContext != null) ? Collections.unmodifiableMap(agentContext) : Collections.emptyMap();
    }

    /**
     * Convenience constructor when no additional agent context is provided.
     * @param toolCallMessage The {@link ToolCallMessage} from the LLM.
     */
    public ToolCalling(ToolCallMessage toolCallMessage) {
        this(toolCallMessage, null);
    }
}

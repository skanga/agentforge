package com.skanga.observability.events;

import com.skanga.core.messages.ToolCallMessage;
import com.skanga.core.messages.ToolCallResultMessage;

import java.util.*;

/**
 * Event data for when an agent has finished executing one or more tools
 * that were requested by an AI model.
 * This event is triggered after the results of the tool executions are available.
 *
 * @param originalToolCallMessage The {@link ToolCallMessage} that was initially received from the LLM
 *                                and triggered these tool executions. Must not be null. This helps
 *                                in correlating which AI request led to these tool calls.
 * @param toolResults             A list of {@link ToolCallResultMessage} objects. Each object in the list
 *                                represents the outcome of a single tool execution corresponding to one of
 *                                the {@link ToolCallMessage.ToolCall} items in the {@code originalToolCallMessage}.
 *                                Must not be null; can be empty if no tools were actually executed or yielded results.
 *                                A defensive copy is made.
 * @param durationMs              The total duration in milliseconds spent executing all the tools
 *                                in this batch (from when {@link ToolCalling} might have been triggered
 *                                to when all results are available).
 * @param agentContext            Optional map of key-value pairs providing context about the
 *                                agent's state or other relevant information after the tool executions.
 *                                Can be null or empty. A defensive copy is made.
 */
public record ToolCalled(
    ToolCallMessage originalToolCallMessage,
    List<ToolCallResultMessage> toolResults,
    long durationMs,
    Map<String, Object> agentContext
) {
    /**
     * Canonical constructor for ToolCalled.
     * Ensures originalToolCallMessage and toolResults are not null.
     * Makes defensive copies of mutable collections.
     */
    public ToolCalled {
        Objects.requireNonNull(originalToolCallMessage, "originalToolCallMessage cannot be null for ToolCalled event.");
        Objects.requireNonNull(toolResults, "toolResults list cannot be null for ToolCalled event.");
        if (durationMs < 0) {
            // Consider if duration can be unknown, e.g. -1. For now, enforce non-negative.
            // throw new IllegalArgumentException("Duration cannot be negative.");
        }
        toolResults = Collections.unmodifiableList(new ArrayList<>(toolResults));
        agentContext = (agentContext != null) ? Collections.unmodifiableMap(agentContext) : Collections.emptyMap();
    }

    /**
     * Convenience constructor when no additional agent context is provided.
     * @param originalToolCallMessage The initiating {@link ToolCallMessage}.
     * @param toolResults             The list of {@link ToolCallResultMessage}s.
     * @param durationMs              The total duration of tool execution.
     */
    public ToolCalled(ToolCallMessage originalToolCallMessage, List<ToolCallResultMessage> toolResults, long durationMs) {
        this(originalToolCallMessage, toolResults, durationMs, null);
    }
}

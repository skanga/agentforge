package com.skanga.observability.events;

import com.skanga.core.messages.MessageRequest;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Event data for when an agent's primary chat interaction (e.g., via {@code agent.chat()}
 * or {@code agent.chatAsync()}) is initiated.
 * This typically marks the beginning of a user interaction flow with the agent.
 *
 * @param request The initial {@link MessageRequest} from the user that started the chat flow.
 *                Must not be null.
 * @param agentContext A map containing initial agent configuration or context relevant to this chat.
 *                     Examples: "agent_class", "provider_name", "model_name" (if resolved),
 *                     "initial_instructions_summary", "tools_count".
 *                     Can be null or empty if no specific context is provided.
 *                     The map passed is defensively copied and made unmodifiable.
 */
public record ChatStart(
    MessageRequest request,
    Map<String, Object> agentContext
) {
    /**
     * Canonical constructor for ChatStart.
     * Ensures the request is not null and makes the agentContext map unmodifiable.
     */
    public ChatStart {
        Objects.requireNonNull(request, "MessageRequest cannot be null for ChatStart.");
        agentContext = (agentContext != null) ? Collections.unmodifiableMap(agentContext) : Collections.emptyMap();
    }

    /**
     * Convenience constructor when no specific agent context is provided.
     * @param request The initial {@link MessageRequest}.
     */
    public ChatStart(MessageRequest request) {
        this(request, null);
    }
}

package com.skanga.observability.events;

import com.skanga.chat.messages.Message;
import com.skanga.core.messages.MessageRequest;
import java.util.Objects;

/**
 * Event data for when an agent's primary chat interaction completes,
 * either successfully or with an error (though errors might also trigger a separate {@link AgentError} event).
 * This event marks the end of a user interaction flow that was initiated by a {@link ChatStart} event.
 *
 * @param request  The initial {@link MessageRequest} from the user that started the chat flow.
 *                 Must not be null. This helps correlate with the {@link ChatStart} event.
 * @param response The final {@link Message} response from the agent. Can be null if an error
 *                 occurred before a response could be generated.
 * @param durationMs The total duration of the chat operation in milliseconds, from start to stop.
 */
public record ChatStop(
    MessageRequest request,
    Message response,       // Can be null if error occurred before response generation
    long durationMs
) {
    /**
     * Canonical constructor for ChatStop.
     * Ensures the initial request is not null.
     */
    public ChatStop {
        Objects.requireNonNull(request, "Initial MessageRequest cannot be null for ChatStop.");
        if (durationMs < 0) {
            throw new IllegalArgumentException("Duration cannot be negative.");
        }
    }
}

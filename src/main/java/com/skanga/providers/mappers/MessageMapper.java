
// mappers/MessageMapper.java
package com.skanga.providers.mappers;

import com.skanga.chat.messages.Message;
import java.util.List;
import java.util.Map;

/**
 * Interface for message mappers.
 * Message mappers are responsible for transforming a list of generic
 * {@link com.skanga.chat.messages.Message} objects into a provider-specific
 * format, typically a list of maps (or DTOs that serialize to maps/JSON objects)
 * suitable for inclusion in an API request body.
 *
 * Each AI provider (OpenAI, Anthropic, etc.) will have its own implementation
 * of this interface to handle its unique message structure requirements.
 */
public interface MessageMapper {
    /**
     * Maps a list of generic Message objects to a list of provider-specific format.
     *
     * The output is a {@code List<Map<String, Object>>} where each map represents
     * a single message formatted according to the target AI provider's API specification.
     *
     * @param messages The list of Message objects to map. These messages
     *                 can be of various roles (user, assistant, tool, system) and may contain
     *                 different types of content (text, tool calls, tool results, attachments).
     * @return A list of maps, where each map represents a message structured for the specific AI provider.
     *         Returns an empty list if the input `messages` is null or empty.
     * @throws com.skanga.core.exceptions.ProviderException if a message cannot be mapped due to
     *         unsupported roles, content types, or attachment issues specific to the provider.
     */
    List<Map<String, Object>> map(List<Message> messages);
}

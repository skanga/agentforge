
// openai/dto/OpenAIStreamChoice.java
package com.skanga.providers.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenAIStreamChoice(
    Integer index,
    OpenAIStreamDelta delta,
    @JsonProperty("finish_reason") String finishReason
) {}

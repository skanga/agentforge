
// openai/dto/OpenAIStreamData.java
package com.skanga.providers.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OpenAIStreamData(
    String id,
    String object,
    Long created,
    String model,
    @JsonProperty("system_fingerprint") String systemFingerprint,
    List<OpenAIStreamChoice> choices
) {}

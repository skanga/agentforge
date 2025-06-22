package com.skanga.providers.ollama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map; // For options

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaEmbeddingRequest(
    String model, // model name
    String prompt, // text to embed
    Map<String, Object> options // Optional model parameters
) {
    // Constructor without options
    public OllamaEmbeddingRequest(String model, String prompt) {
        this(model, prompt, null);
    }
}

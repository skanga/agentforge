package com.skanga.rag.postprocessing.cohere.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CohereRerankResultItem(
    Integer index, // The index of the document in the original input list
    @JsonProperty("relevance_score") Double relevanceScore, // The reranking score
    // "document" field can be here if "return_documents": true was in request
    // For now, we assume we'll map back using the index.
    CohereDocument document // Optional document text if requested
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CohereDocument(String text){}
}

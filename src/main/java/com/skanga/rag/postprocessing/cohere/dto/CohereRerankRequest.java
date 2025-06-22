package com.skanga.rag.postprocessing.cohere.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CohereRerankRequest(
    String model, // Optional, Cohere will use a default if not specified
    String query,
    List<String> documents, // List of document texts
    @JsonProperty("top_n") Integer topN, // Optional, how many documents to return
    @JsonProperty("return_documents") Boolean returnDocuments, // Optional, whether to return document text. Default false.
    @JsonProperty("max_chunks_per_doc") Integer maxChunksPerDoc // Optional
    // Other fields like "user_id", "query_id" can be added if needed
) {
    // Constructor for essential fields
    public CohereRerankRequest(String query, List<String> documents, Integer topN, String model) {
        this(model, query, documents, topN, false, null); // return_documents=false by default
    }

    public CohereRerankRequest(String query, List<String> documents, Integer topN) {
        this(null, query, documents, topN, false, null);
    }
}

package com.skanga.rag.postprocessing.cohere.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CohereRerankResponse(
    String id, // A unique ID for the request
    List<CohereRerankResultItem> results,
    @JsonProperty("meta") MetaInfo meta // Meta information about the API version
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MetaInfo(
        @JsonProperty("api_version") ApiVersion apiVersion
        // BilledUnits billedUnits; // Could also be here
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiVersion(
        String version
        // Boolean isDeprecated;
        // Boolean isExperimental;
    ) {}
}

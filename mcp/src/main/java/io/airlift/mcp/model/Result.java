package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

public sealed interface Result
        extends Meta
        permits CallToolResponse, GetPromptResponse, ReadResourceResponse
{
    @JsonProperty
    Optional<ResultType> resultType();
}

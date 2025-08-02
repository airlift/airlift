package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

public interface Paginated
{
    @JsonProperty
    Optional<String> nextCursor();
}

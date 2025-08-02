package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

public interface Pagination
{
    Pagination NONE = Optional::empty;

    @JsonProperty
    Optional<String> cursor();
}

package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Optional;

public interface Meta<T extends Meta<T>>
{
    @JsonProperty("_meta")
    Optional<Map<String, Object>> meta();

    T withMeta(Map<String, Object> meta);
}

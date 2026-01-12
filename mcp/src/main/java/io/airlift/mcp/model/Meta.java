package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Optional;

public interface Meta
{
    @JsonProperty("_meta")
    Optional<Map<String, Object>> meta();

    Object withMeta(Map<String, Object> meta);
}

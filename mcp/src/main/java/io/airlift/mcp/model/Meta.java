package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

public interface Meta<T extends Meta<T>>
{
    @JsonProperty("_meta")
    Optional<Map<String, Object>> meta();

    T withMeta(Map<String, Object> meta);

    @SuppressWarnings("OptionalAssignedToNull")
    static Optional<Map<String, Object>> normalize(Optional<Map<String, Object>> from)
    {
        if (from == null) {
            return Optional.empty();
        }

        return from.map(map -> map.entrySet()
                .stream()
                .filter(entry -> (entry.getKey() != null) && (entry.getValue() != null))
                .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)));
    }
}

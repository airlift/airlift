package io.airlift.mcp.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record ElicitRequestForm(Optional<String> mode, String message, ObjectNode requestedSchema, Optional<Map<String, Object>> meta)
        implements Meta<ElicitRequestForm>, ElicitRequest
{
    public ElicitRequestForm
    {
        mode = requireNonNullElse(mode, Optional.empty());
        requireNonNull(message, "message is null");
        requireNonNull(requestedSchema, "requestedSchema is null");
        meta = requireNonNullElse(meta, Optional.<Map<String, Object>>empty()).map(ImmutableMap::copyOf);
    }

    public ElicitRequestForm(String message, ObjectNode requestedSchema)
    {
        this(Optional.of("form"), message, requestedSchema, Optional.empty());
    }

    @Override
    public ElicitRequestForm withMeta(Map<String, Object> meta)
    {
        return new ElicitRequestForm(mode, message, requestedSchema, Optional.of(meta));
    }
}

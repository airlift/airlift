package io.airlift.mcp.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record ElicitRequestForm(Optional<String> mode, String message, ObjectNode requestedSchema, Optional<Map<String, Object>> meta, OptionalInt ttl)
        implements Meta, TaskMetadata
{
    public ElicitRequestForm
    {
        mode = requireNonNullElse(mode, Optional.empty());
        requireNonNull(message, "message is null");
        requireNonNull(requestedSchema, "requestedSchema is null");
        meta = requireNonNullElse(meta, Optional.empty());
        ttl = requireNonNullElse(ttl, OptionalInt.empty());
    }

    public ElicitRequestForm(String message, ObjectNode requestedSchema)
    {
        this(Optional.of("form"), message, requestedSchema, Optional.empty(), OptionalInt.empty());
    }

    @Override
    public ElicitRequestForm withMeta(Map<String, Object> meta)
    {
        return new ElicitRequestForm(mode, message, requestedSchema, Optional.of(meta), ttl);
    }
}

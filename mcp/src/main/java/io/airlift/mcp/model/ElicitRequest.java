package io.airlift.mcp.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ElicitRequest(String message, ObjectNode requestedSchema, Optional<Map<String, Object>> meta)
        implements Meta
{
    public ElicitRequest
    {
        requireNonNull(message, "message is null");
        requireNonNull(requestedSchema, "requestedSchema is null");
        requireNonNull(meta, "meta is null");
    }

    public ElicitRequest(String message, ObjectNode requestedSchema)
    {
        this(message, requestedSchema, Optional.empty());
    }
}

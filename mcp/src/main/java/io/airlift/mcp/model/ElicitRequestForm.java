package io.airlift.mcp.model;

import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record ElicitRequestForm(Optional<String> mode, String message, ObjectNode requestedSchema, Optional<Map<String, Object>> meta)
        implements Meta
{
    public ElicitRequestForm
    {
        mode = requireNonNullElse(mode, Optional.empty());
        requireNonNull(message, "message is null");
        requireNonNull(requestedSchema, "requestedSchema is null");
        meta = requireNonNullElse(meta, Optional.empty());
    }

    public ElicitRequestForm(String message, ObjectNode requestedSchema)
    {
        this(Optional.of("form"), message, requestedSchema, Optional.empty());
    }
}

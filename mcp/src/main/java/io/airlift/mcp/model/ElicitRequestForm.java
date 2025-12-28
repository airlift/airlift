package io.airlift.mcp.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record ElicitRequestForm(Optional<String> mode, String message, ObjectNode requestedSchema, Optional<Map<String, Object>> meta)
        implements Meta
{
    public ElicitRequestForm
    {
        mode = firstNonNull(mode, Optional.empty());
        requireNonNull(message, "message is null");
        requireNonNull(requestedSchema, "requestedSchema is null");
        meta = firstNonNull(meta, Optional.empty());
    }

    public ElicitRequestForm(String message, ObjectNode requestedSchema)
    {
        this(Optional.of("form"), message, requestedSchema, Optional.empty());
    }
}

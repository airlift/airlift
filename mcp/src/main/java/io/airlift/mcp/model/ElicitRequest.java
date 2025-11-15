package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

public record ElicitRequest(String message, JsonSchema requestedSchema)
{
    public ElicitRequest
    {
        requireNonNull(message, "message is null");
        requireNonNull(requestedSchema, "requestedSchema is null");
    }
}

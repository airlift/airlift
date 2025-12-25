package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

public record ResourcesUpdatedNotification(String uri)
{
    public ResourcesUpdatedNotification
    {
        requireNonNull(uri, "uri is null");
    }
}

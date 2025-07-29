package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

public record SubscribeResourceRequest(String uri)
{
    public SubscribeResourceRequest
    {
        requireNonNull(uri, "uri is null");
    }
}

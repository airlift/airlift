package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

public record SubscribeRequest(String uri)
{
    public SubscribeRequest
    {
        requireNonNull(uri, "uri is null");
    }
}

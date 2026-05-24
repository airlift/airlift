package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

public record InputRequest(String method, Object params)
{
    public InputRequest
    {
        requireNonNull(method, "method is null");
        requireNonNull(params, "params is null");
    }
}

package io.airlift.mcp.client.internal;

import io.airlift.http.client.Response;

import static java.util.Objects.requireNonNull;

public record RequestResult<R>(Response response, R result)
{
    public RequestResult
    {
        requireNonNull(response, "response is null");
        // result can be null
    }
}

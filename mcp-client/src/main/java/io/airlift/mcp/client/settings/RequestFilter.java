package io.airlift.mcp.client.settings;

import io.airlift.http.client.Request;

public interface RequestFilter
{
    Request.Builder apply(Request.Builder builder);

    default RequestFilter andThen(RequestFilter after)
    {
        return b -> after.apply(apply(b));
    }
}

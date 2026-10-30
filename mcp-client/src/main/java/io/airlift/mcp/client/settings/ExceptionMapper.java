package io.airlift.mcp.client.settings;

import io.airlift.http.client.Request;

public interface ExceptionMapper
{
    RuntimeException mapException(Throwable exception);

    @SuppressWarnings("unused")
    default RuntimeException mapException(Request request, Throwable exception)
    {
        return mapException(exception);
    }
}

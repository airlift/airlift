package io.airlift.jsonrpc.server;

import static java.util.Objects.requireNonNull;

public record JsonRpcMetadata(String uriPath)
{
    public JsonRpcMetadata
    {
        requireNonNull(uriPath, "uriPath is null");
    }
}

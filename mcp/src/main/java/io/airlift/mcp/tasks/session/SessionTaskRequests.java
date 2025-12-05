package io.airlift.mcp.tasks.session;

import io.airlift.mcp.model.JsonRpcRequest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Objects.requireNonNull;

public record SessionTaskRequests(List<JsonRpcRequest<?>> requests)
{
    public SessionTaskRequests
    {
        // don't copy any collections
        requireNonNull(requests, "requests is null");
    }

    public SessionTaskRequests()
    {
        this(new CopyOnWriteArrayList<>());
    }
}

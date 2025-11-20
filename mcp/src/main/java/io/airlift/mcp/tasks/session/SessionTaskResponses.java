package io.airlift.mcp.tasks.session;

import io.airlift.mcp.model.JsonRpcResponse;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

public record SessionTaskResponses(Map<UUID, Optional<JsonRpcResponse<?>>> responses)
{
    public SessionTaskResponses
    {
        // don't copy any collections
        requireNonNull(responses, "responses is null");
    }

    public SessionTaskResponses()
    {
        this(new ConcurrentHashMap<>());
    }
}

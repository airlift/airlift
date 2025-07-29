package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record CancellationRequest(Object requestId, Optional<String> reason)
{
    public CancellationRequest
    {
        requireNonNull(requestId, "requestId is null");
        requireNonNull(reason, "reason is null");
    }

    public CancellationRequest(Object requestId)
    {
        this(requestId, Optional.empty());
    }
}

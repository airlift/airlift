package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record CancelledNotification(Object requestId, Optional<String> reason)
{
    public CancelledNotification
    {
        requireNonNull(requestId, "requestId is null");
        requireNonNull(reason, "reason is null");
    }
}

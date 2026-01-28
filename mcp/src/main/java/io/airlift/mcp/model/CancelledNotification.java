package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

public record CancelledNotification(Object requestId, Optional<String> reason)
{
    public CancelledNotification
    {
        requestId = requireNonNullElse(requestId, "");
        reason = requireNonNullElse(reason, Optional.empty());
    }
}

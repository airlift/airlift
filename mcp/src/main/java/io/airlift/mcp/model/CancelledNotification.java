package io.airlift.mcp.model;

import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;

public record CancelledNotification(Object requestId, Optional<String> reason)
{
    public CancelledNotification
    {
        requestId = firstNonNull(requestId, "");
        reason = firstNonNull(reason, Optional.empty());
    }
}

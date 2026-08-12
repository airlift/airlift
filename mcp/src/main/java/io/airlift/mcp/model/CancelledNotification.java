package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

public record CancelledNotification(Object requestId, Optional<String> reason, Optional<Map<String, Object>> meta)
        implements Meta<CancelledNotification>
{
    public CancelledNotification
    {
        requestId = requireNonNullElse(requestId, "");
        reason = requireNonNullElse(reason, Optional.empty());
        meta = requireNonNullElse(meta, Optional.<Map<String, Object>>empty()).map(ImmutableMap::copyOf);
    }

    @Override
    public CancelledNotification withMeta(Map<String, Object> meta)
    {
        return new CancelledNotification(requestId, reason, Optional.ofNullable(meta));
    }
}

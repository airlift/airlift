package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNullElse;

public record CancelledNotification(Object requestId, Optional<String> reason, Optional<Map<String, Object>> meta)
        implements Meta<CancelledNotification>
{
    public CancelledNotification
    {
        requestId = requireNonNullElse(requestId, "");
        reason = requireNonNullElse(reason, Optional.empty());
        meta = normalize(meta);
    }

    @Override
    public CancelledNotification withMeta(Map<String, Object> meta)
    {
        return new CancelledNotification(requestId, reason, Optional.ofNullable(meta));
    }
}

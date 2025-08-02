package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record CreateMessageResult(Role role, Content content, String model, Optional<StopReason> stopReason)
{
    public CreateMessageResult
    {
        requireNonNull(role, "role is null");
        requireNonNull(content, "content is null");
        requireNonNull(model, "model is null");
        requireNonNull(stopReason, "stopReason is null");
    }
}

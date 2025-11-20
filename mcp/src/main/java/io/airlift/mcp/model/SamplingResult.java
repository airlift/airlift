package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record SamplingResult(Role role, Content content, String model, Optional<String> stopReason)
{
    public SamplingResult
    {
        requireNonNull(role, "role is null");
        requireNonNull(content, "content is null");
        requireNonNull(model, "model is null");
        requireNonNull(stopReason, "stopReason is null");
    }
}

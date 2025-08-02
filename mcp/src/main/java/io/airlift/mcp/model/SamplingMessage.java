package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

public record SamplingMessage(Role role, Content content)
{
    public SamplingMessage
    {
        requireNonNull(role, "role is null");
        requireNonNull(content, "content is null");
    }
}

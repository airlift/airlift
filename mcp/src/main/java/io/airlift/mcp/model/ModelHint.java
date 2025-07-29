package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

public record ModelHint(String name)
{
    public ModelHint
    {
        requireNonNull(name, "name is null");
    }
}

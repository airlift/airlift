package io.airlift.mcp.testing;

import static java.util.Objects.requireNonNull;

public record TestingIdentity(String name)
{
    public TestingIdentity
    {
        requireNonNull(name, "name is null");
    }
}

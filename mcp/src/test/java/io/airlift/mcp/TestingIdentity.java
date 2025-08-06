package io.airlift.mcp;

import static java.util.Objects.requireNonNull;

public record TestingIdentity(String name)
{
    public TestingIdentity
    {
        requireNonNull(name, "name is null");
    }
}

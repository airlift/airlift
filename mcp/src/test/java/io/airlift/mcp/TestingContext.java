package io.airlift.mcp;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

public record TestingContext(Instant now)
{
    public TestingContext
    {
        requireNonNull(now, "now is null");
    }

    public TestingContext()
    {
        this(Instant.now());
    }
}

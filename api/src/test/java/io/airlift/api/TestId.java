package io.airlift.api;

import static java.util.Objects.requireNonNull;

public record TestId(String id)
{
    public TestId
    {
        requireNonNull(id, "id is null");
    }
}

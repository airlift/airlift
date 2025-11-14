package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

public record Event(long id, String data)
{
    public Event
    {
        requireNonNull(data, "data is null");
    }
}

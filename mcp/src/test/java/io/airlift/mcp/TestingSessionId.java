package io.airlift.mcp;

import io.airlift.mcp.session.SessionId;

import java.util.UUID;

import static java.util.Objects.requireNonNull;

public record TestingSessionId(UUID id)
        implements SessionId
{
    public TestingSessionId
    {
        requireNonNull(id, "id is null");
    }

    public static TestingSessionId random()
    {
        return new TestingSessionId(UUID.randomUUID());
    }

    public static TestingSessionId parse(String id)
    {
        return new TestingSessionId(UUID.fromString(id));
    }

    @Override
    public String asString()
    {
        return id.toString();
    }
}

package io.airlift.mcp.client.settings;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ProgressToken(Optional<Object> token)
{
    public ProgressToken
    {
        requireNonNull(token, "token is null");
    }

    public ProgressToken()
    {
        this(Optional.empty());
    }

    public ProgressToken(Object token)
    {
        this(Optional.of(token));
    }
}

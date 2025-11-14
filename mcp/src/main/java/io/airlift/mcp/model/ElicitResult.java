package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ElicitResult<T>(Action action, Optional<T> content)
{
    public ElicitResult
    {
        requireNonNull(action, "action is null");
        requireNonNull(content, "content is null");
    }

    public enum Action
    {
        accept,
        decline,
        cancel,
    }
}

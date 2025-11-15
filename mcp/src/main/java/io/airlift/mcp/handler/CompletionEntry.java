package io.airlift.mcp.handler;

import io.airlift.mcp.model.CompleteReference;

import static java.util.Objects.requireNonNull;

public record CompletionEntry(CompleteReference reference, CompletionHandler handler)
{
    public CompletionEntry
    {
        requireNonNull(reference, "reference is null");
        requireNonNull(handler, "handler is null");
    }
}

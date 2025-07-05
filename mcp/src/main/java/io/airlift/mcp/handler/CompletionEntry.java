package io.airlift.mcp.handler;

import static java.util.Objects.requireNonNull;

public record CompletionEntry(String completionName, CompletionHandler completionHandler)
{
    public CompletionEntry
    {
        requireNonNull(completionName, "completionName is null");
        requireNonNull(completionHandler, "completionHandler is null");
    }
}

package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

public record CompletionResult(Completion completion)
{
    public CompletionResult
    {
        requireNonNull(completion, "completion is null");
    }
}

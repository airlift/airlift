package io.airlift.mcp.handler;

import io.airlift.mcp.model.Prompt;

import static java.util.Objects.requireNonNull;

public record PromptEntry(Prompt prompt, PromptHandler promptHandler)
{
    public PromptEntry
    {
        requireNonNull(prompt, "prompt is null");
        requireNonNull(promptHandler, "promptHandler is null");
    }
}

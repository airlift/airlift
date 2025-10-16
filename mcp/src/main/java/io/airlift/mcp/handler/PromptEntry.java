package io.airlift.mcp.handler;

import static java.util.Objects.requireNonNull;

import io.airlift.mcp.model.Prompt;

public record PromptEntry(Prompt prompt, PromptHandler promptHandler) {
    public PromptEntry {
        requireNonNull(prompt, "prompt is null");
        requireNonNull(promptHandler, "promptHandler is null");
    }
}

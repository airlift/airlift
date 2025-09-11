package io.airlift.mcp.handler;

import io.airlift.mcp.model.Tool;

import static java.util.Objects.requireNonNull;

public record ToolEntry(Tool tool, ToolHandler<?> toolHandler)
{
    public ToolEntry
    {
        requireNonNull(tool, "tool is null");
        requireNonNull(toolHandler, "toolHandler is null");
    }
}

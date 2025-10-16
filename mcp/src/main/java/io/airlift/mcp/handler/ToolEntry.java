package io.airlift.mcp.handler;

import static java.util.Objects.requireNonNull;

import io.airlift.mcp.model.Tool;

public record ToolEntry(Tool tool, ToolHandler toolHandler) {
    public ToolEntry {
        requireNonNull(tool, "tool is null");
        requireNonNull(toolHandler, "toolHandler is null");
    }
}

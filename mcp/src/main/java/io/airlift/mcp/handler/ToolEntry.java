package io.airlift.mcp.handler;

import io.airlift.mcp.model.TaskSupport;
import io.airlift.mcp.model.Tool;

import static java.util.Objects.requireNonNull;

/**
 * @param taskSupport whether the tool can be executed as a task
 */
public record ToolEntry(Tool tool, ToolHandler toolHandler, TaskSupport taskSupport)
{
    public ToolEntry
    {
        requireNonNull(tool, "tool is null");
        requireNonNull(toolHandler, "toolHandler is null");
        requireNonNull(taskSupport, "taskSupport is null");
    }

    public ToolEntry(Tool tool, ToolHandler toolHandler)
    {
        this(tool, toolHandler, TaskSupport.NONE);
    }
}

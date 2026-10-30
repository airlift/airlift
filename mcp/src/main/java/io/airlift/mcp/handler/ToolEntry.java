package io.airlift.mcp.handler;

import io.airlift.mcp.model.TaskSupport;
import io.airlift.mcp.model.Tool;

import static io.airlift.mcp.model.Tool.validateName;
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

        // a tool this server registers is held to the naming rules, while one read from another server is not
        validateName(tool.name());
    }

    public ToolEntry(Tool tool, ToolHandler toolHandler)
    {
        this(tool, toolHandler, TaskSupport.NONE);
    }
}

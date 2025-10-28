package io.airlift.mcp;

import io.airlift.mcp.handler.PromptHandler;
import io.airlift.mcp.handler.ResourceHandler;
import io.airlift.mcp.handler.ToolHandler;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.Tool;

public interface McpServer
{
    void stop();

    void addTool(Tool tool, ToolHandler toolHandler);

    void removeTool(String toolName);

    void addPrompt(Prompt prompt, PromptHandler promptHandler);

    void removePrompt(String promptName);

    void addResource(Resource resource, ResourceHandler handler);

    void removeResource(String resourceUri);
}

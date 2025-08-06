package io.airlift.mcp.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.McpServer;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.PromptHandler;
import io.airlift.mcp.handler.ResourceEntry;
import io.airlift.mcp.handler.ResourceHandler;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.handler.ToolHandler;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.Tool;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class ReferenceServer
        implements McpServer
{
    private static final Logger log = Logger.get(ReferenceServer.class);

    private final McpStatelessSyncServer server;
    private final ObjectMapper objectMapper;

    @Inject
    public ReferenceServer(McpStatelessSyncServer server, ObjectMapper objectMapper, Set<ToolEntry> tools, Set<PromptEntry> prompts, Set<ResourceEntry> resources)
    {
        this.server = requireNonNull(server, "server is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");

        tools.forEach(tool -> addTool(tool.tool(), tool.toolHandler()));
        prompts.forEach(prompt -> addPrompt(prompt.prompt(), prompt.promptHandler()));
        resources.forEach(resource -> addResource(resource.resource(), resource.handler()));
    }

    @PreDestroy
    @Override
    public void stop()
    {
        try {
            server.closeGracefully()
                    .block(Duration.ofSeconds(15));
        }
        catch (Exception e) {
            log.error("Server did not shut down properly", e);
        }
    }

    @Override
    public void addTool(Tool tool, ToolHandler toolHandler)
    {
        server.addTool(Mapper.mapTool(objectMapper, tool, toolHandler));
    }

    @Override
    public void removeTool(String toolName)
    {
        server.removeTool(toolName);
    }

    @Override
    public void addPrompt(Prompt prompt, PromptHandler promptHandler)
    {
        server.addPrompt(Mapper.mapPrompt(prompt, promptHandler));
    }

    @Override
    public void removePrompt(String promptName)
    {
        server.removePrompt(promptName);
    }

    @Override
    public void addResource(Resource resource, ResourceHandler handler)
    {
        server.addResource(Mapper.mapResource(resource, handler));
    }

    @Override
    public void removeResource(String resourceName)
    {
        server.removeResource(resourceName);
    }
}

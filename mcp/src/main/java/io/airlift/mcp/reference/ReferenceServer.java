package io.airlift.mcp.reference;

import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.McpServer;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.PromptHandler;
import io.airlift.mcp.handler.RequestContextProvider;
import io.airlift.mcp.handler.ResourceEntry;
import io.airlift.mcp.handler.ResourceHandler;
import io.airlift.mcp.handler.ResourceTemplateEntry;
import io.airlift.mcp.handler.ResourceTemplateHandler;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.handler.ToolHandler;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.session.McpSessionController;
import io.airlift.mcp.session.McpValueKey;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.util.McpUriTemplateManager;
import io.modelcontextprotocol.util.McpUriTemplateManagerFactory;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.airlift.mcp.session.McpValueKey.PROMPTS_LIST_VERSION;
import static io.airlift.mcp.session.McpValueKey.RESOURCES_LIST_VERSION;
import static io.airlift.mcp.session.McpValueKey.RESOURCE_VERSION;
import static io.airlift.mcp.session.McpValueKey.TOOLS_LIST_VERSION;
import static java.util.Objects.requireNonNull;

public class ReferenceServer
        implements McpServer
{
    private static final Logger log = Logger.get(ReferenceServer.class);

    private final McpStatelessSyncServer server;
    private final McpJsonMapper mcpJsonMapper;
    private final McpUriTemplateManagerFactory uriTemplateManagerFactory;
    private final RequestContextProvider requestContextProvider;
    private final Optional<McpSessionController> sessionController;

    @Inject
    public ReferenceServer(
            McpStatelessSyncServer server,
            McpJsonMapper mcpJsonMapper,
            McpUriTemplateManagerFactory uriTemplateManagerFactory,
            Set<ToolEntry> tools,
            Set<PromptEntry> prompts,
            Set<ResourceEntry> resources,
            Set<ResourceTemplateEntry> resourceTemplates,
            RequestContextProvider requestContextProvider,
            Optional<McpSessionController> sessionController)
    {
        this.server = requireNonNull(server, "server is null");
        this.mcpJsonMapper = requireNonNull(mcpJsonMapper, "objectMapper is null");
        this.uriTemplateManagerFactory = requireNonNull(uriTemplateManagerFactory, "uriTemplateManagerFactory is null");
        this.requestContextProvider = requireNonNull(requestContextProvider, "requestContextProvider is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");

        tools.forEach(tool -> addTool(tool.tool(), tool.toolHandler()));
        prompts.forEach(prompt -> addPrompt(prompt.prompt(), prompt.promptHandler()));
        resources.forEach(resource -> addResource(resource.resource(), resource.handler()));
        resourceTemplates.forEach(resourceTemplate -> addResourceTemplate(resourceTemplate.resourceTemplate(), resourceTemplate.handler()));
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
        server.addTool(Mapper.mapTool(requestContextProvider, mcpJsonMapper, tool, toolHandler));
        postChangedEvent(TOOLS_LIST_VERSION);
    }

    @Override
    public void removeTool(String toolName)
    {
        server.removeTool(toolName);
        postChangedEvent(TOOLS_LIST_VERSION);
    }

    @Override
    public void addPrompt(Prompt prompt, PromptHandler promptHandler)
    {
        server.addPrompt(Mapper.mapPrompt(requestContextProvider, prompt, promptHandler));
        postChangedEvent(PROMPTS_LIST_VERSION);
    }

    @Override
    public void removePrompt(String promptName)
    {
        server.removePrompt(promptName);
        postChangedEvent(PROMPTS_LIST_VERSION);
    }

    @Override
    public void addResource(Resource resource, ResourceHandler handler)
    {
        server.addResource(Mapper.mapResource(requestContextProvider, resource, handler));
        postChangedEvent(RESOURCES_LIST_VERSION);
    }

    @Override
    public void removeResource(String resourceUri)
    {
        server.removeResource(resourceUri);
        postChangedEvent(RESOURCES_LIST_VERSION);
    }

    @Override
    public void notifyResourceChanged(String resourceUri)
    {
        postChangedEvent(RESOURCE_VERSION.withSuffix(resourceUri));
    }

    @Override
    public void addResourceTemplate(ResourceTemplate resourceTemplate, ResourceTemplateHandler handler)
    {
        McpUriTemplateManager manager = uriTemplateManagerFactory.create(resourceTemplate.uriTemplate());
        server.addResourceTemplate(Mapper.mapResourceTemplate(requestContextProvider, resourceTemplate, handler, manager::extractVariableValues));
        postChangedEvent(RESOURCES_LIST_VERSION);
    }

    @Override
    public void removeResourceTemplate(String uriTemplate)
    {
        server.removeResourceTemplate(uriTemplate);
        postChangedEvent(RESOURCES_LIST_VERSION);
    }

    private void postChangedEvent(McpValueKey<UUID> key)
    {
        sessionController.ifPresent(controller -> controller.currentSessionIds()
                .forEach(sessionId -> controller.upsertValue(sessionId, key, UUID.randomUUID())));
    }
}

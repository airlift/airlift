package io.airlift.mcp.reference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
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
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.ListRootsResult;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.RootsList;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.session.McpSessionController;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.util.McpUriTemplateManager;
import io.modelcontextprotocol.util.McpUriTemplateManagerFactory;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

import static io.airlift.mcp.model.JsonRpcRequest.JSON_RPC_VERSION;
import static io.airlift.mcp.session.McpValueKey.ROOTS;
import static io.airlift.mcp.session.McpValueKey.resourceSubscription;
import static java.util.Objects.requireNonNull;

public class ReferenceServer
        implements McpServer
{
    public static final String LIST_ROOTS_REQUEST_ID = "internal-mcp-list-roots-request";

    private static final Logger log = Logger.get(ReferenceServer.class);

    private final McpStatelessSyncServer server;
    private final McpJsonMapper mcpJsonMapper;
    private final McpUriTemplateManagerFactory uriTemplateManagerFactory;
    private final RequestContextProvider requestContextProvider;
    private final Optional<McpSessionController> sessionController;
    private final ObjectMapper objectMapper;

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
            Optional<McpSessionController> sessionController,
            ResponseListenerController responseListenerController,
            ObjectMapper objectMapper)
    {
        this.server = requireNonNull(server, "server is null");
        this.mcpJsonMapper = requireNonNull(mcpJsonMapper, "objectMapper is null");
        this.uriTemplateManagerFactory = requireNonNull(uriTemplateManagerFactory, "uriTemplateManagerFactory is null");
        this.requestContextProvider = requireNonNull(requestContextProvider, "requestContextProvider is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");

        tools.forEach(tool -> addTool(tool.tool(), tool.toolHandler()));
        prompts.forEach(prompt -> addPrompt(prompt.prompt(), prompt.promptHandler()));
        resources.forEach(resource -> addResource(resource.resource(), resource.handler()));
        resourceTemplates.forEach(resourceTemplate -> addResourceTemplate(resourceTemplate.resourceTemplate(), resourceTemplate.handler()));

        responseListenerController.addListener(this::handleRpcResponse);
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
        postChangedEvent("notifications/tools/list_changed", Optional.empty(), (_, _) -> true);
    }

    @Override
    public void removeTool(String toolName)
    {
        server.removeTool(toolName);
        postChangedEvent("notifications/tools/list_changed", Optional.empty(), (_, _) -> true);
    }

    @Override
    public void addPrompt(Prompt prompt, PromptHandler promptHandler)
    {
        server.addPrompt(Mapper.mapPrompt(requestContextProvider, prompt, promptHandler));
        postChangedEvent("notifications/prompts/list_changed", Optional.empty(), (_, _) -> true);
    }

    @Override
    public void removePrompt(String promptName)
    {
        server.removePrompt(promptName);
        postChangedEvent("notifications/prompts/list_changed", Optional.empty(), (_, _) -> true);
    }

    @Override
    public void addResource(Resource resource, ResourceHandler handler)
    {
        server.addResource(Mapper.mapResource(requestContextProvider, resource, handler));
        postChangedEvent("notifications/resources/list_changed", Optional.empty(), (_, _) -> true);
    }

    @Override
    public void removeResource(String resourceUri)
    {
        server.removeResource(resourceUri);
        postChangedEvent("notifications/resources/list_changed", Optional.empty(), (_, _) -> true);
    }

    @Override
    public void notifyResourceChanged(String resourceUri)
    {
        Optional<ImmutableMap<String, Object>> param = Optional.of(ImmutableMap.of("uri", resourceUri));
        postChangedEvent("notifications/resources/updated", param,
                (sessionController, sessionId) -> sessionController.currentValue(sessionId, resourceSubscription(resourceUri)).orElse(false));
    }

    @Override
    public void addResourceTemplate(ResourceTemplate resourceTemplate, ResourceTemplateHandler handler)
    {
        McpUriTemplateManager manager = uriTemplateManagerFactory.create(resourceTemplate.uriTemplate());
        server.addResourceTemplate(Mapper.mapResourceTemplate(requestContextProvider, resourceTemplate, handler, manager::extractVariableValues));
        postChangedEvent("notifications/resources/list_changed", Optional.empty(), (_, _) -> true);
    }

    @Override
    public void removeResourceTemplate(String uriTemplate)
    {
        server.removeResourceTemplate(uriTemplate);
        postChangedEvent("notifications/resources/list_changed", Optional.empty(), (_, _) -> true);
    }

    private void postChangedEvent(String notification, Optional<? extends Map<String, Object>> param, BiPredicate<McpSessionController, ? super String> filter)
    {
        sessionController.ifPresent(controller -> {
            try {
                var request = new JsonRpcRequest<>(JSON_RPC_VERSION, null, notification, param);
                String data = objectMapper.writeValueAsString(request);
                controller.currentSessionIds()
                        .stream()
                        .filter(sessionId -> filter.test(controller, sessionId))
                        .forEach(sessionId -> controller.addEvent(sessionId, data));
            }
            catch (JsonProcessingException e) {
                log.error(e, "Failed to serialize notification: %s", notification);
            }
        });
    }

    private void handleRpcResponse(String sessionId, JsonRpcResponse<?> jsonRpcResponse)
    {
        if (LIST_ROOTS_REQUEST_ID.equals(jsonRpcResponse.id())) {
            sessionController.ifPresent(controller -> jsonRpcResponse.result().ifPresent(result -> {
                // TODO - handle pagination
                ListRootsResult listRootsResult = mcpJsonMapper.convertValue(result, ListRootsResult.class);
                controller.upsertValue(sessionId, ROOTS, new RootsList(listRootsResult.roots()));
            }));
        }
    }
}

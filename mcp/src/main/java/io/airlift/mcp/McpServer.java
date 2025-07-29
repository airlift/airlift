package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.inject.Inject;
import io.airlift.jsonrpc.model.JsonRpcErrorDetail;
import io.airlift.mcp.handler.ResourceTemplatesEntry;
import io.airlift.mcp.handler.ResourcesEntry;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Completion;
import io.airlift.mcp.model.CompletionRequest;
import io.airlift.mcp.model.CompletionResult;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.InitializeResult;
import io.airlift.mcp.model.InitializeResult.ServerCapabilities;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResponse;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.ServerInfo;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.session.SessionId;
import jakarta.ws.rs.core.Request;
import org.glassfish.jersey.uri.UriTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Throwables.getRootCause;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.jsonrpc.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.jsonrpc.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.Constants.PROTOCOL_VERSION;
import static java.util.Objects.requireNonNull;

public class McpServer
{
    private final ServerInfo serverInfo;
    private final McpHandlers handlers;

    @Inject
    public McpServer(ServerInfo serverInfo, McpHandlers handlers)
    {
        this.serverInfo = requireNonNull(serverInfo, "serverInfo is null");
        this.handlers = requireNonNull(handlers, "handlers is null");
    }

    public InitializeResult initialize(InitializeRequest initializeRequest)
    {
        if (!initializeRequest.protocolVersion().equals(PROTOCOL_VERSION)) {
            Map<String, Object> data = ImmutableMap.of("supported", new String[] {PROTOCOL_VERSION}, "requested", initializeRequest.protocolVersion());
            throw JsonRpcErrorDetail.exception(INVALID_PARAMS, "Unsupported protocol version", data);
        }

        ServerCapabilities serverCapabilities = handlers.serverCapabilities();
        return new InitializeResult(PROTOCOL_VERSION, serverCapabilities, new Implementation(serverInfo.serverName(), serverInfo.serverVersion()), serverInfo.instructions());
    }

    public ListToolsResponse listTools()
    {
        List<Tool> toolsList = handlers.streamTools().collect(toImmutableList());
        return new ListToolsResponse(toolsList);
    }

    public CallToolResult callTool(Request request, SessionId sessionId, McpNotifier notifier, CallToolRequest callToolRequest)
            throws McpException
    {
        return handlers.tool(callToolRequest.name())
                .map(toolEntry -> {
                    try {
                        return toolEntry.toolHandler().callTool(request, sessionId, notifier, callToolRequest);
                    }
                    catch (Exception e) {
                        throw handleException(e);
                    }
                })
                .orElseThrow(() -> McpException.exception(INVALID_PARAMS, "Tool not found", ImmutableMap.of("name", callToolRequest.name())));
    }

    public ListPromptsResult listPrompts()
    {
        List<Prompt> pomptsList = handlers.streamPrompts().collect(toImmutableList());
        return new ListPromptsResult(pomptsList);
    }

    public GetPromptResult getPrompt(Request request, SessionId sessionId, McpNotifier notifier, GetPromptRequest getPromptRequest)
            throws McpException
    {
        return handlers.prompt(getPromptRequest.name())
                .map(promptEntry -> {
                    try {
                        return promptEntry.promptHandler().getPrompt(request, sessionId, notifier, getPromptRequest);
                    }
                    catch (Exception e) {
                        throw handleException(e);
                    }
                })
                .orElseThrow(() -> McpException.exception(INVALID_PARAMS, "Prompt not found", ImmutableMap.of("name", getPromptRequest.name())));
    }

    public ListResourcesResult listResources(Request request, SessionId sessionId, McpNotifier notifier)
            throws McpException
    {
        try {
            List<Resource> resourcesList = handlers.streamResources()
                    .flatMap(resource -> resource.listResources(request, sessionId, notifier).resources().stream())
                    .collect(toImmutableList());
            return new ListResourcesResult(resourcesList);
        }
        catch (Exception e) {
            throw handleException(e);
        }
    }

    public ListResourceTemplatesResult listResourceTemplates(Request request, SessionId sessionId, McpNotifier notifier)
            throws McpException
    {
        try {
            List<ResourceTemplate> resoureTemplatesList = handlers.streamResourceTemplates()
                    .flatMap(resource -> resource.listResourceTemplates(request, sessionId, notifier).resourceTemplates().stream())
                    .collect(toImmutableList());
            return new ListResourceTemplatesResult(resoureTemplatesList);
        }
        catch (Exception e) {
            throw handleException(e);
        }
    }

    public ReadResourceResult readResources(Request request, SessionId sessionId, McpNotifier notifier, ReadResourceRequest readResourceRequest)
            throws McpException
    {
        try {
            Stream<ResourceContents> resourceContentsStream = handlers.streamResources()
                    .flatMap(set -> {
                        ResourcesEntry resourcesEntry = set.listResources(request, sessionId, notifier);
                        return resourcesEntry.resources().stream()
                                .filter(resource -> resource.uri().equals(readResourceRequest.uri()))
                                .flatMap(resource -> resourcesEntry.handler().readResource(request, sessionId, notifier, resource, readResourceRequest).stream());
                    });

            Stream<ResourceContents> resourceTemplateContentsStream = handlers.streamResourceTemplates()
                    .flatMap(set -> {
                        ResourceTemplatesEntry resourceTemplatesEntry = set.listResourceTemplates(request, sessionId, notifier);
                        return resourceTemplatesEntry.resourceTemplates().stream()
                                .flatMap(resourceTemplate -> {
                                    UriTemplate uriTemplate = new UriTemplate(resourceTemplate.uriTemplate());
                                    Map<String, String> templateVariableToValue = new HashMap<>();
                                    if (uriTemplate.match(readResourceRequest.uri(), templateVariableToValue)) {
                                        return resourceTemplatesEntry.handler().readResource(request, sessionId, notifier, resourceTemplate, readResourceRequest, templateVariableToValue).stream();
                                    }
                                    return Stream.of();
                                });
                    });

            List<ResourceContents> resourceContentsList = Streams.concat(resourceContentsStream, resourceTemplateContentsStream)
                    .collect(toImmutableList());
            return new ReadResourceResult(resourceContentsList);
        }
        catch (Exception e) {
            throw handleException(e);
        }
    }

    public CompletionResult completeCompletion(Request request, SessionId sessionId, McpNotifier notifier, CompletionRequest completionRequest)
            throws McpException
    {
        try {
            Completion completion = handlers.streamCompletions()
                    .flatMap(completionHandler -> completionHandler.completeCompletion(request, sessionId, notifier, completionRequest).stream())
                    .reduce(new Completion(ImmutableList.of()), this::mergeCompletions);
            return new CompletionResult(completion);
        }
        catch (Exception e) {
            throw handleException(e);
        }
    }

    private Completion mergeCompletions(Completion c1, Completion c2)
    {
        List<String> combined = ImmutableList.<String>builder().addAll(c1.values()).addAll(c2.values()).build();
        Optional<Integer> combinedTotal = (c1.total().isPresent() || c2.total().isPresent())
                ? Optional.of(c1.total().orElse(0) + c2.total().orElse(0))
                : Optional.empty();
        boolean combinedHasMore = c1.hasMore() || c2.hasMore();
        return new Completion(combined, combinedTotal, combinedHasMore);
    }

    private static McpException handleException(Exception exception)
    {
        return new McpException(fromException(exception));
    }

    private static JsonRpcErrorDetail fromException(Exception exception)
    {
        if (getRootCause(exception) instanceof McpException mcpException) {
            return mcpException.errorDetail();
        }

        StringWriter stringWriter = new StringWriter();
        exception.printStackTrace(new PrintWriter(stringWriter));

        return new JsonRpcErrorDetail(INTERNAL_ERROR, stringWriter.toString());
    }
}

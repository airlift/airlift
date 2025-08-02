package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.inject.Inject;
import io.airlift.jsonrpc.model.JsonRpcErrorDetail;
import io.airlift.mcp.handler.CompletionEntry;
import io.airlift.mcp.handler.Handlers;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.RequestContext;
import io.airlift.mcp.handler.ResourceEntry;
import io.airlift.mcp.handler.ResourceTemplateEntry;
import io.airlift.mcp.handler.ResourceTemplateHandler.PathTemplateValues;
import io.airlift.mcp.handler.ToolEntry;
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
import io.airlift.mcp.model.ListChanged;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResponse;
import io.airlift.mcp.model.Paginated;
import io.airlift.mcp.model.Pagination;
import io.airlift.mcp.model.PaginationMetadata;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.ServerInfo;
import io.airlift.mcp.model.SubscribeListChanged;
import io.airlift.mcp.model.Tool;
import org.glassfish.jersey.uri.UriTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
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
    private final PaginationMetadata paginationMetadata;
    private final Handlers<ToolEntry> tools = new Handlers<>();
    private final Handlers<PromptEntry> prompts = new Handlers<>();
    private final Handlers<ResourceEntry> resources = new Handlers<>();
    private final Handlers<ResourceTemplateEntry> resourceTemplates = new Handlers<>();
    private final Handlers<CompletionEntry> completions = new Handlers<>();

    @Inject
    public McpServer(
            ServerInfo serverInfo,
            PaginationMetadata paginationMetadata,
            Map<String, ToolEntry> boundTools,
            Map<String, PromptEntry> boundPrompts,
            Map<String, ResourceEntry> boundResources,
            Map<String, ResourceTemplateEntry> boundResourceTemplates,
            Map<String, CompletionEntry> boundCompletions)
    {
        this.serverInfo = requireNonNull(serverInfo, "serverInfo is null");
        this.paginationMetadata = requireNonNull(paginationMetadata, "paginationMetadata is null");

        boundTools.forEach(tools::add);
        boundPrompts.forEach(prompts::add);
        boundResources.forEach(resources::add);
        boundResourceTemplates.forEach(resourceTemplates::add);
        boundCompletions.forEach(completions::add);
    }

    public Handlers<ToolEntry> tools()
    {
        return tools;
    }

    public Handlers<PromptEntry> prompts()
    {
        return prompts;
    }

    public Handlers<ResourceEntry> resources()
    {
        return resources;
    }

    public Handlers<ResourceTemplateEntry> resourceTemplates()
    {
        return resourceTemplates;
    }

    public Handlers<CompletionEntry> completions()
    {
        return completions;
    }

    public InitializeResult initialize(InitializeRequest initializeRequest)
    {
        if (!initializeRequest.protocolVersion().equals(PROTOCOL_VERSION)) {
            Map<String, Object> data = ImmutableMap.of("supported", new String[] {PROTOCOL_VERSION}, "requested", initializeRequest.protocolVersion());
            throw JsonRpcErrorDetail.exception(INVALID_PARAMS, "Unsupported protocol version", data);
        }

        ServerCapabilities serverCapabilities = new ServerCapabilities(
                completions.isEmpty() ? Optional.empty() : Optional.of(new InitializeResult.CompletionCapabilities()),
                Optional.empty(),
                prompts.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(true)),
                (resources.isEmpty() && resourceTemplates.isEmpty()) ? Optional.empty() : Optional.of(new SubscribeListChanged(false, false)),
                tools.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(false)));

        return new InitializeResult(PROTOCOL_VERSION, serverCapabilities, new Implementation(serverInfo.serverName(), serverInfo.serverVersion()), serverInfo.instructions());
    }

    public ListToolsResponse listTools(Pagination pagination)
    {
        return paginated(tools, pagination, paginationMetadata.pageSize(), entry -> entry.tool().name(), (toolEntries, nextCursor) -> {
            List<Tool> tools = toolEntries.stream().map(ToolEntry::tool).collect(toImmutableList());
            return new ListToolsResponse(tools, nextCursor);
        });
    }

    public CallToolResult callTool(RequestContext requestContext, McpNotifier notifier, CallToolRequest callToolRequest)
            throws McpException
    {
        return tools.entry(callToolRequest.name())
                .map(toolEntry -> {
                    try {
                        return toolEntry.toolHandler().callTool(requestContext, notifier, callToolRequest);
                    }
                    catch (Exception e) {
                        throw handleException(e);
                    }
                })
                .orElseThrow(() -> McpException.exception(INVALID_PARAMS, "Tool not found", ImmutableMap.of("name", callToolRequest.name())));
    }

    public ListPromptsResult listPrompts(Pagination pagination)
    {
        return paginated(prompts, pagination, paginationMetadata.pageSize(), entry -> entry.prompt().name(), (promptEntries, nextCursor) -> {
            List<Prompt> prompts = promptEntries.stream().map(PromptEntry::prompt).collect(toImmutableList());
            return new ListPromptsResult(prompts, nextCursor);
        });
    }

    public GetPromptResult getPrompt(RequestContext requestContext, McpNotifier notifier, GetPromptRequest getPromptRequest)
            throws McpException
    {
        return prompts.entry(getPromptRequest.name())
                .map(promptEntry -> {
                    try {
                        return promptEntry.promptHandler().getPrompt(requestContext, notifier, getPromptRequest);
                    }
                    catch (Exception e) {
                        throw handleException(e);
                    }
                })
                .orElseThrow(() -> McpException.exception(INVALID_PARAMS, "Prompt not found", ImmutableMap.of("name", getPromptRequest.name())));
    }

    public ListResourcesResult listResources(Pagination pagination)
    {
        return paginated(resources, pagination, paginationMetadata.pageSize(), entry -> entry.resource().name(), (promptEntries, nextCursor) -> {
            List<Resource> resources = promptEntries.stream().map(ResourceEntry::resource).collect(toImmutableList());
            return new ListResourcesResult(resources, nextCursor);
        });
    }

    public ListResourceTemplatesResult listResourceTemplates(Pagination pagination)
    {
        return paginated(resourceTemplates, pagination, paginationMetadata.pageSize(), entry -> entry.resourceTemplate().name(), (promptEntries, nextCursor) -> {
            List<ResourceTemplate> resourceTemplates = promptEntries.stream().map(ResourceTemplateEntry::resourceTemplate).collect(toImmutableList());
            return new ListResourceTemplatesResult(resourceTemplates, nextCursor);
        });
    }

    public ReadResourceResult readResources(RequestContext requestContext, McpNotifier notifier, ReadResourceRequest readResourceRequest)
            throws McpException
    {
        try {
            Stream<ResourceContents> resourceContentsStream = resources.entries()
                    .filter(resourceEntry -> resourceEntry.resource().uri().equals(readResourceRequest.uri()))
                    .flatMap(resourceEntry -> resourceEntry.handler().readResource(requestContext, notifier, resourceEntry.resource(), readResourceRequest).stream());

            Stream<ResourceContents> resourceTemplateContentsStream = resourceTemplates.entries()
                    .flatMap(resourceTemplateEntry -> {
                        UriTemplate uriTemplate = new UriTemplate(resourceTemplateEntry.resourceTemplate().uriTemplate());
                        Map<String, String> templateVariableToValue = new HashMap<>();
                        if (uriTemplate.match(readResourceRequest.uri(), templateVariableToValue)) {
                            return resourceTemplateEntry.handler().readResource(requestContext, notifier, resourceTemplateEntry.resourceTemplate(), readResourceRequest, new PathTemplateValues(templateVariableToValue)).stream();
                        }
                        return Stream.of();
                    });

            List<ResourceContents> resourceContentsList = Streams.concat(resourceContentsStream, resourceTemplateContentsStream)
                    .collect(toImmutableList());
            return new ReadResourceResult(resourceContentsList);
        }
        catch (Exception e) {
            throw handleException(e);
        }
    }

    public CompletionResult completeCompletion(RequestContext requestContext, McpNotifier notifier, CompletionRequest completionRequest)
            throws McpException
    {
        try {
            Completion completion = completions.entries()
                    .map(CompletionEntry::completionHandler)
                    .flatMap(completionHandler -> completionHandler.completeCompletion(requestContext, notifier, completionRequest).stream())
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

    private static <T, R extends Paginated> R paginated(Handlers<T> handlers, Pagination pagination, int pageSize, Function<T, String> nameAccessor, BiFunction<List<T>, Optional<String>, R> mapper)
    {
        var adjustedMap = pagination.cursor().map(handlers::entriesAfter).orElseGet(handlers::entries);

        List<T> results = adjustedMap.limit(pageSize)
                .collect(toImmutableList());

        Optional<String> nextCursor = (results.size() >= pageSize)
                ? Optional.of(nameAccessor.apply(results.getLast()))
                : Optional.empty();

        return mapper.apply(results, nextCursor);
    }
}

package io.airlift.mcp.internal;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.airlift.mcp.McpCapabilityFilter;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.handler.CompletionEntry;
import io.airlift.mcp.handler.CompletionHandler;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.PromptHandler;
import io.airlift.mcp.handler.ResourceEntry;
import io.airlift.mcp.handler.ResourceHandler;
import io.airlift.mcp.handler.ResourceTemplateEntry;
import io.airlift.mcp.handler.ResourceTemplateHandler;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.handler.ToolHandler;
import io.airlift.mcp.model.CompleteReference;
import io.airlift.mcp.model.CompleteReference.PromptReference;
import io.airlift.mcp.model.CompleteReference.ResourceReference;
import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.ResourceTemplateValues;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.reflection.IconHelper;
import org.glassfish.jersey.uri.UriTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.McpModule.MCP_SERVER_ICONS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static java.util.Objects.requireNonNull;

public class InternalEntities
        implements McpEntities
{
    private final Map<String, ToolEntry> tools = new ConcurrentHashMap<>();
    private final Map<String, PromptEntry> prompts = new ConcurrentHashMap<>();
    private final Map<URI, ResourceEntry> resources = new ConcurrentHashMap<>();
    private final Map<UriTemplate, ResourceTemplateEntry> resourceTemplates = new ConcurrentHashMap<>();
    private final Map<String, CompletionEntry> completions = new ConcurrentHashMap<>();
    private final McpCapabilityFilter capabilityFilter;
    private final Implementation serverImplementation;

    @Inject
    InternalEntities(
            McpMetadata metadata,
            Set<ToolEntry> tools,
            Set<PromptEntry> prompts,
            Set<ResourceEntry> resources,
            Set<ResourceTemplateEntry> resourceTemplates,
            Set<CompletionEntry> completions,
            IconHelper iconHelper,
            @Named(MCP_SERVER_ICONS) Set<String> serverIcons,
            McpCapabilityFilter capabilityFilter)
    {
        this.capabilityFilter = requireNonNull(capabilityFilter, "capabilityFilter is null");

        tools.forEach(tool -> addTool(tool.tool(), tool.toolHandler()));
        prompts.forEach(prompt -> addPrompt(prompt.prompt(), prompt.promptHandler()));
        resources.forEach(resource -> addResource(resource.resource(), resource.handler()));
        resourceTemplates.forEach(resourceTemplate -> addResourceTemplate(resourceTemplate.resourceTemplate(), resourceTemplate.handler()));
        completions.forEach(completion -> addCompletion(completion.reference(), completion.handler()));

        serverImplementation = iconHelper.mapIcons(serverIcons).map(icons -> metadata.implementation().withAdditionalIcons(icons))
                .orElse(metadata.implementation());
    }

    @Override
    public Implementation serverImplementation()
    {
        return serverImplementation;
    }

    @Override
    public void addTool(Tool tool, ToolHandler toolHandler)
    {
        tools.put(tool.name(), new ToolEntry(tool, toolHandler));
    }

    @Override
    public void removeTool(String toolName)
    {
        tools.remove(toolName);
    }

    @Override
    public void addPrompt(Prompt prompt, PromptHandler promptHandler)
    {
        prompts.put(prompt.name(), new PromptEntry(prompt, promptHandler));
    }

    @Override
    public void removePrompt(String promptName)
    {
        prompts.remove(promptName);
    }

    @Override
    public void addResource(Resource resource, ResourceHandler handler)
    {
        resources.put(toUri(resource.uri()), new ResourceEntry(resource, handler));
    }

    @Override
    public void removeResource(String resourceUri)
    {
        resources.remove(toUri(resourceUri));
    }

    @Override
    public void addResourceTemplate(ResourceTemplate resourceTemplate, ResourceTemplateHandler handler)
    {
        resourceTemplates.put(toUriTemplate(resourceTemplate.uriTemplate()), new ResourceTemplateEntry(resourceTemplate, handler));
    }

    @Override
    public void removeResourceTemplate(String uriTemplate)
    {
        resourceTemplates.remove(toUriTemplate(uriTemplate));
    }

    @Override
    public void addCompletion(CompleteReference reference, CompletionHandler handler)
    {
        completions.put(completionKey(reference), new CompletionEntry(reference, handler));
    }

    @Override
    public void removeCompletion(CompleteReference reference)
    {
        completions.remove(completionKey(reference));
    }

    @Override
    public List<Tool> tools(McpRequestContext requestContext)
    {
        return tools.values()
                .stream()
                .map(ToolEntry::tool)
                .filter(tool -> capabilityFilter.isAllowed(requestContext.identity(), tool))
                .collect(toImmutableList());
    }

    @Override
    public ToolEntry requireTool(McpRequestContext requestContext, String name)
    {
        return Optional.ofNullable(tools.get(name))
                .filter(toolEntry -> {
                    if (!capabilityFilter.isAllowed(requestContext.identity(), toolEntry.tool())) {
                        throw new McpClientException(exception(INVALID_PARAMS, "Tool not allowed: " + name));
                    }
                    return true;
                })
                .orElseThrow(() -> exception(INVALID_PARAMS, "Tool not found: " + name));
    }

    @Override
    public List<Prompt> prompts(McpRequestContext requestContext)
    {
        return prompts.values()
                .stream()
                .map(PromptEntry::prompt)
                .filter(prompt -> capabilityFilter.isAllowed(requestContext.identity(), prompt))
                .collect(toImmutableList());
    }

    @Override
    public PromptEntry requirePrompt(McpRequestContext requestContext, String name)
    {
        return Optional.ofNullable(prompts.get(name))
                .filter(promptEntry -> {
                    if (!capabilityFilter.isAllowed(requestContext.identity(), promptEntry.prompt())) {
                        throw new McpClientException(exception(INVALID_PARAMS, "Prompt not allowed: " + name));
                    }
                    return true;
                })
                .orElseThrow(() -> exception(INVALID_PARAMS, "Prompt not found: " + name));
    }

    @Override
    public List<Resource> resources(McpRequestContext requestContext)
    {
        return resources.values()
                .stream()
                .map(ResourceEntry::resource)
                .filter(resource -> capabilityFilter.isAllowed(requestContext.identity(), resource))
                .collect(toImmutableList());
    }

    @Override
    public ResourceEntry requireResource(McpRequestContext requestContext, String uri)
    {
        return Optional.ofNullable(resources.get(toUri(uri)))
                .filter(resourceEntry -> {
                    if (!capabilityFilter.isAllowed(requestContext.identity(), resourceEntry.resource())) {
                        throw new McpClientException(exception(INVALID_PARAMS, "Resource access not allowed: " + uri));
                    }
                    return true;
                })
                .orElseThrow(() -> exception(INVALID_PARAMS, "Resource not found: " + uri));
    }

    @Override
    public List<ResourceTemplate> resourceTemplates(McpRequestContext requestContext)
    {
        return resourceTemplates.values()
                .stream()
                .map(ResourceTemplateEntry::resourceTemplate)
                .filter(resourceTemplate -> capabilityFilter.isAllowed(requestContext.identity(), resourceTemplate))
                .collect(toImmutableList());
    }

    @Override
    public List<CompleteReference> completions(McpRequestContext requestContext)
    {
        return completions.values()
                .stream()
                .map(CompletionEntry::reference)
                .filter(completeReference -> capabilityFilter.isAllowed(requestContext.identity(), completeReference))
                .collect(toImmutableList());
    }

    @Override
    public Optional<CompletionEntry> completion(McpRequestContext requestContext, CompleteReference reference)
    {
        return Optional.ofNullable(completions.get(completionKey(reference)))
                .filter(completionEntry -> capabilityFilter.isAllowed(requestContext.identity(), completionEntry.reference()));
    }

    @Override
    public Optional<List<ResourceContents>> readResourceContents(McpRequestContext requestContext, ReadResourceRequest readResourceRequest)
    {
        if (!capabilityFilter.isAllowed(requestContext.identity(), readResourceRequest.uri())) {
            throw new McpClientException(exception(INVALID_PARAMS, "Resource access not allowed: " + readResourceRequest.uri()));
        }

        URI uri = URI.create(readResourceRequest.uri());
        ResourceEntry resourceEntry = resources.get(uri);
        if (resourceEntry != null) {
            if (!capabilityFilter.isAllowed(requestContext.identity(), resourceEntry.resource())) {
                throw new McpClientException(exception(INVALID_PARAMS, "Resource access not allowed: " + readResourceRequest.uri()));
            }
        }

        for (Map.Entry<UriTemplate, ResourceTemplateEntry> entry : resourceTemplates.entrySet()) {
            if (entry.getKey().match(readResourceRequest.uri(), new HashMap<>())) {
                if (!capabilityFilter.isAllowed(requestContext.identity(), entry.getValue().resourceTemplate())) {
                    throw new McpClientException(exception(INVALID_PARAMS, "Resource access not allowed: " + readResourceRequest.uri()));
                }
                break;
            }
        }

        return findResource(readResourceRequest.uri())
                .map(readResourceEntry -> readResourceEntry.handler().readResource(requestContext, readResourceEntry.resource(), readResourceRequest))
                .or(() -> findResourceTemplate(readResourceRequest.uri()).map(match -> match.entry.handler().readResourceTemplate(requestContext, match.entry.resourceTemplate(), readResourceRequest, match.values)));
    }

    private Optional<ResourceEntry> findResource(String uriString)
    {
        URI uri = toUri(uriString);

        return resources.entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(uri))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private record ResourceTemplateMatch(ResourceTemplateEntry entry, ResourceTemplateValues values) {}

    private Optional<ResourceTemplateMatch> findResourceTemplate(String uri)
    {
        return resourceTemplates.entrySet()
                .stream()
                .flatMap(entry -> {
                    UriTemplate uriTemplate = entry.getKey();
                    Map<String, String> variables = new HashMap<>();
                    if (uriTemplate.match(uri, variables)) {
                        return Stream.of(new ResourceTemplateMatch(entry.getValue(), new ResourceTemplateValues(variables)));
                    }
                    return Stream.of();
                })
                .findFirst();
    }

    private static URI toUri(String uriString)
    {
        try {
            return URI.create(uriString);
        }
        catch (IllegalArgumentException e) {
            throw exception(INVALID_REQUEST, "Invalid URI: " + uriString);
        }
    }

    private static UriTemplate toUriTemplate(String uriString)
    {
        try {
            return new UriTemplate(uriString);
        }
        catch (IllegalArgumentException e) {
            throw exception(INVALID_REQUEST, "Invalid URI: " + uriString);
        }
    }

    private static String completionKey(CompleteReference reference)
    {
        return switch (reference) {
            case PromptReference(var name, _) -> name;
            case ResourceReference(var uri) -> uri;
        };
    }
}

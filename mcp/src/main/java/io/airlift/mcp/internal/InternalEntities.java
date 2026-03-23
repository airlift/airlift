package io.airlift.mcp.internal;

import com.google.inject.Inject;
import io.airlift.mcp.McpCapabilityFilter;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpEntities;
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
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.ResourceTemplateValues;
import io.airlift.mcp.model.Tool;
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

    @Inject
    InternalEntities(
            Set<ToolEntry> tools,
            Set<PromptEntry> prompts,
            Set<ResourceEntry> resources,
            Set<ResourceTemplateEntry> resourceTemplates,
            Set<CompletionEntry> completions,
            McpCapabilityFilter capabilityFilter)
    {
        this.capabilityFilter = requireNonNull(capabilityFilter, "capabilityFilter is null");

        tools.forEach(tool -> addTool(tool.tool(), tool.toolHandler()));
        prompts.forEach(prompt -> addPrompt(prompt.prompt(), prompt.promptHandler()));
        resources.forEach(resource -> addResource(resource.resource(), resource.handler()));
        resourceTemplates.forEach(resourceTemplate -> addResourceTemplate(resourceTemplate.resourceTemplate(), resourceTemplate.handler()));
        completions.forEach(completion -> addCompletion(completion.reference(), completion.handler()));
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
    public Optional<ToolEntry> toolEntry(McpRequestContext requestContext, String toolName)
    {
        return Optional.ofNullable(tools.get(toolName))
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.tool()));
    }

    @Override
    public void validateToolAllowed(McpRequestContext requestContext, String toolName)
    {
        Optional.ofNullable(tools.get(toolName))
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.tool()))
                .or(() -> {
                    throw new McpClientException(exception(INVALID_PARAMS, "Tool access not allowed: " + toolName));
                });
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
    public Optional<PromptEntry> promptEntry(McpRequestContext requestContext, String promptName)
    {
        return Optional.ofNullable(prompts.get(promptName))
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.prompt()));
    }

    @Override
    public void validatePromptAllowed(McpRequestContext requestContext, String promptName)
    {
        Optional.ofNullable(prompts.get(promptName))
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.prompt()))
                .or(() -> {
                    throw new McpClientException(exception(INVALID_PARAMS, "Prompt access not allowed: " + promptName));
                });
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
    public Optional<ResourceEntry> resourceEntry(McpRequestContext requestContext, String uri)
    {
        return Optional.ofNullable(resources.get(toUri(uri)))
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.resource()));
    }

    @Override
    public void validateResourceAllowed(McpRequestContext requestContext, String uri)
    {
        Optional.ofNullable(resources.get(toUri(uri)))
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.resource()))
                .or(() -> {
                    throw new McpClientException(exception(INVALID_PARAMS, "Resource access not allowed: " + uri));
                });
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
    public Optional<CompletionEntry> completionEntry(McpRequestContext requestContext, CompleteReference ref)
    {
        return Optional.ofNullable(completions.get(completionKey(ref)))
                .filter(entry -> capabilityFilter.isAllowed(requestContext.identity(), entry.reference()));
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

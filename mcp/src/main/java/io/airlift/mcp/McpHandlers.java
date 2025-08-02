package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.airlift.mcp.handler.CompletionHandler;
import io.airlift.mcp.handler.ListResourceTemplatesHandler;
import io.airlift.mcp.handler.ListResourcesHandler;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.PromptHandler;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.handler.ToolHandler;
import io.airlift.mcp.model.InitializeResult;
import io.airlift.mcp.model.InitializeResult.LoggingCapabilities;
import io.airlift.mcp.model.InitializeResult.ServerCapabilities;
import io.airlift.mcp.model.ListChanged;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.SubscribeListChanged;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.session.NotificationType;
import io.airlift.mcp.session.SessionMetadata;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static io.airlift.mcp.session.NotificationType.PROMPTS_LIST_CHANGED;
import static io.airlift.mcp.session.NotificationType.RESOURCES_LIST_CHANGED;
import static io.airlift.mcp.session.NotificationType.RESOURCE_UPDATED;
import static io.airlift.mcp.session.NotificationType.SERVER_TO_CLIENT_LOGGING;
import static io.airlift.mcp.session.NotificationType.TOOLS_LIST_CHANGED;
import static java.util.Objects.requireNonNull;

public class McpHandlers
{
    private final Map<String, ToolEntry> tools;
    private final Map<String, PromptEntry> prompts;
    private final Set<ListResourcesHandler> resources;
    private final Set<ListResourceTemplatesHandler> resourceTemplates;
    private final Set<CompletionHandler> completions;
    private final SessionMetadata sessionMetadata;

    @Inject
    public McpHandlers(Map<String, ToolEntry> boundTools,
            Map<String, PromptEntry> boundPrompts,
            Set<ListResourcesHandler> boundResources,
            Set<ListResourceTemplatesHandler> boundResourceTemplates,
            Set<CompletionHandler> boundCompletions,
            SessionMetadata sessionMetadata)
    {
        tools = new ConcurrentHashMap<>(boundTools);
        prompts = new ConcurrentHashMap<>(boundPrompts);
        resources = Sets.newConcurrentHashSet(boundResources);
        resourceTemplates = Sets.newConcurrentHashSet(boundResourceTemplates);
        completions = Sets.newConcurrentHashSet(boundCompletions);
        this.sessionMetadata = requireNonNull(sessionMetadata, "sessionMetadata is null");
    }

    public ServerCapabilities serverCapabilities()
    {
        Set<NotificationType> supportedNotifications = sessionMetadata.supportedNotifications();

        return new ServerCapabilities(
                completions.isEmpty() ? Optional.empty() : Optional.of(new InitializeResult.CompletionCapabilities()),
                supportedNotifications.contains(SERVER_TO_CLIENT_LOGGING) ? Optional.of(new LoggingCapabilities()) : Optional.empty(),
                prompts.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(supportedNotifications.contains(PROMPTS_LIST_CHANGED))),
                (resources.isEmpty() && resourceTemplates.isEmpty()) ? Optional.empty() : Optional.of(new SubscribeListChanged(supportedNotifications.contains(RESOURCE_UPDATED), supportedNotifications.contains(RESOURCES_LIST_CHANGED))),
                tools.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(supportedNotifications.contains(TOOLS_LIST_CHANGED))));
    }

    public void addTool(Tool tool, ToolHandler toolHandler)
    {
        if (tools.put(tool.name(), new ToolEntry(tool, toolHandler)) != null) {
            throw new IllegalArgumentException("Tool with name " + tool.name() + " already exists");
        }
    }

    public boolean removeTool(String toolName)
    {
        return tools.remove(toolName) != null;
    }

    public Map<String, ToolEntry> tools()
    {
        return ImmutableMap.copyOf(tools);
    }

    public Optional<ToolEntry> tool(String toolName)
    {
        return Optional.ofNullable(tools.get(toolName));
    }

    public Stream<Tool> streamTools()
    {
        return tools.values().stream().map(ToolEntry::tool);
    }

    public void addPrompt(Prompt prompt, PromptHandler promptHandler)
    {
        if (prompts.put(prompt.name(), new PromptEntry(prompt, promptHandler)) != null) {
            throw new IllegalArgumentException("Prompt with name " + prompt.name() + " already exists");
        }
    }

    public boolean removePrompt(String promptName)
    {
        return prompts.remove(promptName) != null;
    }

    public Map<String, PromptEntry> prompts()
    {
        return ImmutableMap.copyOf(prompts);
    }

    public Stream<Prompt> streamPrompts()
    {
        return prompts.values().stream().map(PromptEntry::prompt);
    }

    public Optional<PromptEntry> prompt(String promptName)
    {
        return Optional.ofNullable(prompts.get(promptName));
    }

    public void addResource(ListResourcesHandler resourcesHandler)
    {
        if (!resources.add(resourcesHandler)) {
            throw new IllegalArgumentException("Resources handler already exists");
        }
    }

    public void addResourceTemplate(ListResourceTemplatesHandler resourceTemplatesHandler)
    {
        if (!resourceTemplates.add(resourceTemplatesHandler)) {
            throw new IllegalArgumentException("Resource templates handler already exists");
        }
    }

    public boolean removeResource(ListResourcesHandler resourcesHandler)
    {
        return resources.remove(resourcesHandler);
    }

    public boolean removeResourceTemplate(ListResourceTemplatesHandler resourceTemplatesHandler)
    {
        return resourceTemplates.remove(resourceTemplatesHandler);
    }

    public Set<ListResourcesHandler> resources()
    {
        return ImmutableSet.copyOf(resources);
    }

    public Set<ListResourceTemplatesHandler> resourceTemplates()
    {
        return ImmutableSet.copyOf(resourceTemplates);
    }

    public Stream<ListResourcesHandler> streamResources()
    {
        return resources.stream();
    }

    public Stream<ListResourceTemplatesHandler> streamResourceTemplates()
    {
        return resourceTemplates.stream();
    }

    public void addCompletion(CompletionHandler completion)
    {
        if (!completions.add(completion)) {
            throw new IllegalArgumentException("Completion already exists");
        }
    }

    public boolean removeCompletion(CompletionHandler completion)
    {
        return completions.remove(completion);
    }

    public Set<CompletionHandler> completions()
    {
        return ImmutableSet.copyOf(completions);
    }

    public Stream<CompletionHandler> streamCompletions()
    {
        return completions.stream();
    }
}

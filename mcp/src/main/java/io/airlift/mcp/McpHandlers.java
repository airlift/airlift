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
import io.airlift.mcp.model.InitializeResult.ServerCapabilities;
import io.airlift.mcp.model.ListChanged;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.SubscribeListChanged;
import io.airlift.mcp.model.Tool;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class McpHandlers
{
    private final Map<String, ToolEntry> tools;
    private final Map<String, PromptEntry> prompts;
    private final Set<ListResourcesHandler> resources;
    private final Set<ListResourceTemplatesHandler> resourceTemplates;
    private final Set<CompletionHandler> completions;

    @Inject
    public McpHandlers(Map<String, ToolEntry> boundTools,
            Map<String, PromptEntry> boundPrompts,
            Set<ListResourcesHandler> boundResources,
            Set<ListResourceTemplatesHandler> boundResourceTemplates,
            Set<CompletionHandler> boundCompletions)
    {
        tools = new ConcurrentHashMap<>(boundTools);
        prompts = new ConcurrentHashMap<>(boundPrompts);
        resources = Sets.newConcurrentHashSet(boundResources);
        resourceTemplates = Sets.newConcurrentHashSet(boundResourceTemplates);
        completions = Sets.newConcurrentHashSet(boundCompletions);
    }

    public ServerCapabilities serverCapabilities()
    {
        return new ServerCapabilities(
                completions.isEmpty() ? Optional.empty() : Optional.of(new InitializeResult.CompletionCapabilities()),
                Optional.empty(),
                prompts.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(true)),
                (resources.isEmpty() && resourceTemplates.isEmpty()) ? Optional.empty() : Optional.of(new SubscribeListChanged(false, false)),
                tools.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(false)));
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

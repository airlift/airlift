package io.airlift.mcp.internal;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import io.airlift.json.JsonSubType;
import io.airlift.json.JsonSubTypeBinder;
import io.airlift.jsonrpc.JsonRpcMethod;
import io.airlift.jsonrpc.JsonRpcModule;
import io.airlift.mcp.McpCompletion;
import io.airlift.mcp.McpHandlers;
import io.airlift.mcp.McpModule;
import io.airlift.mcp.McpPrompt;
import io.airlift.mcp.McpResources;
import io.airlift.mcp.McpServer;
import io.airlift.mcp.McpTool;
import io.airlift.mcp.handler.CompletionHandler;
import io.airlift.mcp.handler.ListResourceTemplatesHandler;
import io.airlift.mcp.handler.ListResourcesHandler;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.ResourceTemplatesEntry;
import io.airlift.mcp.handler.ResourcesEntry;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.model.CompletionReference;
import io.airlift.mcp.model.Content;
import io.airlift.mcp.model.Content.AudioContent;
import io.airlift.mcp.model.Content.EmbeddedResource;
import io.airlift.mcp.model.Content.ImageContent;
import io.airlift.mcp.model.Content.ResourceLink;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.ServerInfo;
import io.airlift.mcp.reflection.CompletionHandlerProvider;
import io.airlift.mcp.reflection.ListResourceTemplatesHandlerProvider;
import io.airlift.mcp.reflection.ListResourcesHandlerProvider;
import io.airlift.mcp.reflection.PromptHandlerProvider;
import io.airlift.mcp.reflection.ToolHandlerProvider;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.MapBinder.newMapBinder;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.json.JsonSubTypeBinder.jsonSubTypeBinder;
import static io.airlift.mcp.reflection.ReflectionHelper.forAllInClass;

public class InternalMcpModule
{
    private InternalMcpModule() {}

    public static McpModule.Builder builder()
    {
        return new McpModule.Builder()
        {
            private final JsonRpcModule.Builder<?> jsonRpcBuilder = JsonRpcModule.builder().withBasePath("mcp");
            private final ImmutableSet.Builder<Class<?>> instances = ImmutableSet.builder();
            private final ImmutableMap.Builder<String, ToolHandlerProvider> tools = ImmutableMap.builder();
            private final ImmutableMap.Builder<String, PromptHandlerProvider> prompts = ImmutableMap.builder();
            private final ImmutableSet.Builder<CompletionHandlerProvider> completions = ImmutableSet.builder();
            private final ImmutableSet.Builder<ListResourcesHandlerProvider> resources = ImmutableSet.builder();
            private final ImmutableSet.Builder<ListResourceTemplatesHandlerProvider> resourceTemplates = ImmutableSet.builder();
            private ServerInfo serverInfo = new ServerInfo("MCP", "1.0.0", "");

            @Override
            public McpModule.Builder withServerInfo(String serverName, String serverVersion, String instructions)
            {
                this.serverInfo = new ServerInfo(serverName, serverVersion, instructions);
                return this;
            }

            @Override
            public McpModule.Builder withBasePath(String basePath)
            {
                jsonRpcBuilder.withBasePath(basePath);
                return this;
            }

            @Override
            public McpModule.Builder add(JsonRpcMethod jsonRpcMethod)
            {
                jsonRpcBuilder.add(jsonRpcMethod);
                return this;
            }

            @Override
            public McpModule.Builder addAllInClass(Class<?> clazz)
            {
                instances.add(clazz);

                addToolsInClass(clazz);
                addPromptsInClass(clazz);
                addCompletionsInClass(clazz);
                addResourceListsInClass(clazz);

                return this;
            }

            @Override
            public Module build()
            {
                JsonRpcMethod.addAllInClass(jsonRpcBuilder, InternalRpcMethods.class);

                return binder -> {
                    binder.bind(McpServer.class).in(SINGLETON);
                    binder.bind(McpHandlers.class).in(SINGLETON);

                    binder.install(jsonRpcBuilder.build());
                    binder.bind(ServerInfo.class).toInstance(serverInfo);

                    instances.build().forEach(instance -> binder.bind(instance).in(SINGLETON));

                    bindTools(binder);
                    bindPrompts(binder);
                    bindResources(binder);
                    bindCompletions(binder);

                    bindJsonSubTypes(binder);
                };
            }

            private void bindResources(Binder binder)
            {
                Multibinder<ListResourcesHandler> listResourcesBinder = newSetBinder(binder, ListResourcesHandler.class);
                resources.build().forEach(provider -> listResourcesBinder.addBinding().toProvider(provider));

                Multibinder<ListResourceTemplatesHandler> listResourceTemplatesBinder = newSetBinder(binder, ListResourceTemplatesHandler.class);
                resourceTemplates.build().forEach(provider -> listResourceTemplatesBinder.addBinding().toProvider(provider));
            }

            private static void bindJsonSubTypes(Binder binder)
            {
                JsonSubTypeBinder jsonSubTypeBinder = jsonSubTypeBinder(binder);

                JsonSubType contentJsonSubType = JsonSubType.builder()
                        .forBase(Content.class, "type")
                        .add(TextContent.class, "text")
                        .add(ImageContent.class, "image")
                        .add(AudioContent.class, "audio")
                        .add(EmbeddedResource.class, "resource")
                        .add(ResourceLink.class, "resource_link")
                        .build();
                jsonSubTypeBinder.bindJsonSubType(contentJsonSubType);

                JsonSubType completionSubType = JsonSubType.builder()
                        .forBase(CompletionReference.class, "type")
                        .add(CompletionReference.Prompt.class, CompletionReference.Prompt.TYPE)
                        .add(CompletionReference.Resource.class, CompletionReference.Resource.TYPE)
                        .build();
                jsonSubTypeBinder.bindJsonSubType(completionSubType);
            }

            private void bindCompletions(Binder binder)
            {
                Multibinder<CompletionHandler> completionBinder = newSetBinder(binder, CompletionHandler.class);
                completions.build().forEach(completion -> completionBinder.addBinding().toProvider(completion));
            }

            private void bindPrompts(Binder binder)
            {
                MapBinder<String, PromptEntry> promptBinder = newMapBinder(binder, String.class, PromptEntry.class);
                prompts.build().forEach((prompt, promptHandlerProvider) -> promptBinder.addBinding(prompt).toProvider(promptHandlerProvider));
            }

            private void bindTools(Binder binder)
            {
                MapBinder<String, ToolEntry> toolBinder = newMapBinder(binder, String.class, ToolEntry.class);
                tools.build().forEach((tool, toolHandlerProvider) -> toolBinder.addBinding(tool).toProvider(toolHandlerProvider));
            }

            private void addResourceListsInClass(Class<?> clazz)
            {
                forAllInClass(clazz, McpResources.class, (_, method, parameters) -> {
                    if (method.getReturnType().equals(ResourcesEntry.class)) {
                        resources.add(new ListResourcesHandlerProvider(clazz, method, parameters));
                    }
                    else if (method.getReturnType().equals(ResourceTemplatesEntry.class)) {
                        resourceTemplates.add(new ListResourceTemplatesHandlerProvider(clazz, method, parameters));
                    }
                    else {
                        throw new IllegalArgumentException("Unsupported return type for method Should be ResourcesList or ResourceTemplatesList. Method: %s ".formatted(method));
                    }
                });
            }

            private void addCompletionsInClass(Class<?> clazz)
            {
                forAllInClass(clazz, McpCompletion.class, (_, method, parameters) ->
                        completions.add(new CompletionHandlerProvider(clazz, method, parameters)));
            }

            private void addPromptsInClass(Class<?> clazz)
            {
                forAllInClass(clazz, McpPrompt.class, (mcpPrompt, method, parameters) ->
                        prompts.put(mcpPrompt.name(), new PromptHandlerProvider(mcpPrompt, clazz, method, parameters, mcpPrompt.role())));
            }

            private void addToolsInClass(Class<?> clazz)
            {
                forAllInClass(clazz, McpTool.class, (mcpTool, method, parameters) ->
                        tools.put(mcpTool.name(), new ToolHandlerProvider(mcpTool, clazz, method, parameters)));
            }
        };
    }
}

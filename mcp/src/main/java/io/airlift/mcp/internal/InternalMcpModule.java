package io.airlift.mcp.internal;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.OptionalBinder;
import io.airlift.jaxrs.JaxrsBinder;
import io.airlift.json.JsonSubType;
import io.airlift.json.JsonSubTypeBinder;
import io.airlift.jsonrpc.JsonRpcMethod;
import io.airlift.jsonrpc.JsonRpcModule;
import io.airlift.jsonrpc.JsonRpcRequestFilter;
import io.airlift.mcp.McpCompletion;
import io.airlift.mcp.McpModule;
import io.airlift.mcp.McpPrompt;
import io.airlift.mcp.McpResource;
import io.airlift.mcp.McpResourceTemplate;
import io.airlift.mcp.McpServer;
import io.airlift.mcp.McpTool;
import io.airlift.mcp.handler.CompletionEntry;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.ResourceEntry;
import io.airlift.mcp.handler.ResourceTemplateEntry;
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
import io.airlift.mcp.reflection.PromptHandlerProvider;
import io.airlift.mcp.reflection.ResourceHandlerProvider;
import io.airlift.mcp.reflection.ResourceTemplateHandlerProvider;
import io.airlift.mcp.reflection.ToolHandlerProvider;
import io.airlift.mcp.session.SessionController;
import io.airlift.mcp.session.SessionMetadata;
import org.glassfish.jersey.server.model.Resource;

import java.util.Optional;
import java.util.function.Consumer;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.MapBinder.newMapBinder;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static io.airlift.json.JsonSubTypeBinder.jsonSubTypeBinder;
import static io.airlift.mcp.reflection.ReflectionHelper.forAllInClass;
import static java.util.Objects.requireNonNull;

public class InternalMcpModule
{
    private static final String DEFAULT_MCP_PATH = "mcp";

    private InternalMcpModule() {}

    public static McpModule.Builder builder()
    {
        return new McpModule.Builder()
        {
            private final JsonRpcModule.Builder<?> jsonRpcBuilder = JsonRpcModule.builder().withBasePath(DEFAULT_MCP_PATH);
            private final ImmutableSet.Builder<Class<?>> instances = ImmutableSet.builder();
            private final ImmutableMap.Builder<String, ToolHandlerProvider> tools = ImmutableMap.builder();
            private final ImmutableMap.Builder<String, PromptHandlerProvider> prompts = ImmutableMap.builder();
            private final ImmutableMap.Builder<String, CompletionHandlerProvider> completions = ImmutableMap.builder();
            private final ImmutableMap.Builder<String, ResourceHandlerProvider> resources = ImmutableMap.builder();
            private final ImmutableMap.Builder<String, ResourceTemplateHandlerProvider> resourceTemplates = ImmutableMap.builder();
            private Optional<Consumer<LinkedBindingBuilder<SessionController>>> sessionControllerBinding = Optional.empty();
            private String mcpPath = DEFAULT_MCP_PATH;
            private SessionMetadata sessionMetadata = SessionMetadata.DEFAULT;
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
                this.mcpPath = requireNonNull(basePath, "basePath is null");
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
                addResourcesInClass(clazz);
                addResourceTemplatesInClass(clazz);

                return this;
            }

            @Override
            public McpModule.Builder withRequestFilter(Consumer<LinkedBindingBuilder<JsonRpcRequestFilter>> binding)
            {
                jsonRpcBuilder.withRequestFilter(binding);
                return this;
            }

            @Override
            public McpModule.Builder withSessionHandling(SessionMetadata sessionMetadata, Consumer<LinkedBindingBuilder<SessionController>> binding)
            {
                this.sessionMetadata = requireNonNull(sessionMetadata, "sessionMetadata is null");
                sessionControllerBinding = Optional.of(binding);
                return this;
            }

            @Override
            public Module build()
            {
                JsonRpcMethod.addAllInClass(jsonRpcBuilder, InternalRpcMethods.class);

                return binder -> {
                    binder.bind(McpServer.class).in(SINGLETON);
                    binder.bind(ServerInfo.class).toInstance(serverInfo);
                    binder.install(jsonRpcBuilder.build());

                    instances.build().forEach(instance -> binder.bind(instance).in(SINGLETON));

                    bindTools(binder);
                    bindPrompts(binder);
                    bindResources(binder);
                    bindResourceTemplates(binder);
                    bindCompletions(binder);
                    bindSessionHandling(binder);
                    bindJsonSubTypes(binder);
                };
            }

            private void bindSessionHandling(Binder binder)
            {
                binder.bind(SessionMetadata.class).toInstance(sessionMetadata);

                OptionalBinder<SessionController> sessionBinder = OptionalBinder.newOptionalBinder(binder, SessionController.class);

                JaxrsBinder jaxrsBinder = jaxrsBinder(binder);
                jaxrsBinder.bind(SessionIdValueProvider.class);

                sessionControllerBinding.ifPresent(binding -> {
                    binding.accept(sessionBinder.setBinding());

                    Resource resource = Resource.builder(InternalSessionResource.class)
                            .path(mcpPath)
                            .build();

                    jaxrsBinder.bind(InternalSessionResource.class);
                    jaxrsBinder.bindInstance(resource);
                });
            }

            private void bindJsonSubTypes(Binder binder)
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
                MapBinder<String, CompletionEntry> completionBinder = newMapBinder(binder, String.class, CompletionEntry.class);
                completions.build().forEach((completion, completionHandlerProvider) -> completionBinder.addBinding(completion).toProvider(completionHandlerProvider));
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

            private void bindResources(Binder binder)
            {
                MapBinder<String, ResourceEntry> resourceBinder = newMapBinder(binder, String.class, ResourceEntry.class);
                resources.build().forEach((resource, resourceHandlerProvider) -> resourceBinder.addBinding(resource).toProvider(resourceHandlerProvider));
            }

            private void bindResourceTemplates(Binder binder)
            {
                MapBinder<String, ResourceTemplateEntry> resourceTemplateBinder = newMapBinder(binder, String.class, ResourceTemplateEntry.class);
                resourceTemplates.build().forEach((resource, resourceHandlerProvider) -> resourceTemplateBinder.addBinding(resource).toProvider(resourceHandlerProvider));
            }

            private void addResourcesInClass(Class<?> clazz)
            {
                forAllInClass(clazz, McpResource.class, (mcpResource, method, parameters) ->
                        resources.put(mcpResource.name(), new ResourceHandlerProvider(mcpResource, clazz, method, parameters)));
            }

            private void addResourceTemplatesInClass(Class<?> clazz)
            {
                forAllInClass(clazz, McpResourceTemplate.class, (mcpResourceTemplate, method, parameters) ->
                        resourceTemplates.put(mcpResourceTemplate.name(), new ResourceTemplateHandlerProvider(mcpResourceTemplate, clazz, method, parameters)));
            }

            private void addCompletionsInClass(Class<?> clazz)
            {
                forAllInClass(clazz, McpCompletion.class, (mcpCompletion, method, parameters) ->
                        completions.put(mcpCompletion.name(), new CompletionHandlerProvider(mcpCompletion, clazz, method, parameters)));
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

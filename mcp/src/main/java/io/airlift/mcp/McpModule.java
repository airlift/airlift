package io.airlift.mcp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Names;
import io.airlift.json.JsonSubType;
import io.airlift.json.JsonSubTypeBinder;
import io.airlift.mcp.handler.CompletionEntry;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.ResourceEntry;
import io.airlift.mcp.handler.ResourceTemplateEntry;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.internal.InternalMcpModule;
import io.airlift.mcp.model.CompleteReference;
import io.airlift.mcp.model.CompleteReference.PromptReference;
import io.airlift.mcp.model.CompleteReference.ResourceReference;
import io.airlift.mcp.model.Content;
import io.airlift.mcp.model.Content.AudioContent;
import io.airlift.mcp.model.Content.EmbeddedResource;
import io.airlift.mcp.model.Content.ImageContent;
import io.airlift.mcp.model.Content.ResourceLink;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.Icon;
import io.airlift.mcp.reflection.CompletionHandlerProvider;
import io.airlift.mcp.reflection.IconHelper;
import io.airlift.mcp.reflection.IdentityMapperMetadata;
import io.airlift.mcp.reflection.PromptHandlerProvider;
import io.airlift.mcp.reflection.ResourceHandlerProvider;
import io.airlift.mcp.reflection.ResourceTemplateHandlerProvider;
import io.airlift.mcp.reflection.ToolHandlerProvider;
import io.airlift.mcp.sessions.SessionController;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.airlift.json.JsonSubTypeBinder.jsonSubTypeBinder;
import static io.airlift.mcp.McpMetadata.DEFAULT;
import static io.airlift.mcp.McpModule.Mode.STANDARD;
import static io.airlift.mcp.reflection.ReflectionHelper.forAllInClass;
import static java.util.Objects.requireNonNull;

public class McpModule
        implements Module
{
    public static final String MCP_SERVER_ICONS = "mcp-server-icons";

    private final Mode mode;
    private final McpMetadata metadata;
    private final IdentityMapperBinding identityMapperBinding;
    private final Set<Class<?>> classes;
    private final Set<ToolHandlerProvider> tools;
    private final Set<PromptHandlerProvider> prompts;
    private final Set<ResourceHandlerProvider> resources;
    private final Set<ResourceTemplateHandlerProvider> resourceTemplates;
    private final Set<CompletionHandlerProvider> completions;
    private final Optional<Consumer<LinkedBindingBuilder<SessionController>>> sessionControllerBinding;
    private final Consumer<LinkedBindingBuilder<McpCancellationHandler>> cancellationHandlerBinding;
    private final Map<String, Consumer<LinkedBindingBuilder<Icon>>> icons;
    private final Set<String> serverIcons;

    public static Builder builder()
    {
        return new Builder();
    }

    private McpModule(
            Mode mode,
            McpMetadata metadata,
            IdentityMapperBinding identityMapperBinding,
            Set<Class<?>> classes,
            Set<ToolHandlerProvider> tools,
            Set<PromptHandlerProvider> prompts,
            Set<ResourceHandlerProvider> resources,
            Set<ResourceTemplateHandlerProvider> resourceTemplates,
            Set<CompletionHandlerProvider> completions,
            Optional<Consumer<LinkedBindingBuilder<SessionController>>> sessionControllerBinding,
            Consumer<LinkedBindingBuilder<McpCancellationHandler>> cancellationHandlerBinding,
            Map<String, Consumer<LinkedBindingBuilder<Icon>>> icons,
            Set<String> serverIcons)
    {
        this.mode = requireNonNull(mode, "mode is null");
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.identityMapperBinding = requireNonNull(identityMapperBinding, "identityMapperBinding is null");
        this.classes = ImmutableSet.copyOf(classes);
        this.tools = ImmutableSet.copyOf(tools);
        this.prompts = ImmutableSet.copyOf(prompts);
        this.resources = ImmutableSet.copyOf(resources);
        this.resourceTemplates = ImmutableSet.copyOf(resourceTemplates);
        this.completions = ImmutableSet.copyOf(completions);
        this.sessionControllerBinding = requireNonNull(sessionControllerBinding, "sessionControllerBinding is null");
        this.cancellationHandlerBinding = requireNonNull(cancellationHandlerBinding, "cancellationHandlerBinding is null");
        this.icons = ImmutableMap.copyOf(icons);
        this.serverIcons = ImmutableSet.copyOf(serverIcons);
    }

    public enum Mode
    {
        STANDARD,
        UNBOUND_IMPLEMENTATION,
    }

    record IdentityMapperBinding(Class<?> identityType, Consumer<AnnotatedBindingBuilder<McpIdentityMapper>> identityMapperBinding)
    {
        IdentityMapperBinding
        {
            requireNonNull(identityType, "identityType is null");
            requireNonNull(identityMapperBinding, "identityMapperBinding is null");
        }
    }

    @VisibleForTesting
    public static JsonSubType buildJsonSubType()
    {
        return JsonSubType.builder()
                .forBase(Content.class, "type")
                .add(TextContent.class, "text")
                .add(ImageContent.class, "image")
                .add(AudioContent.class, "audio")
                .add(EmbeddedResource.class, "resource")
                .add(ResourceLink.class, "resource_link")
                .forBase(CompleteReference.class, "type")
                .add(PromptReference.class, "ref/prompt")
                .add(ResourceReference.class, "ref/resource")
                .build();
    }

    public static class Builder
    {
        private final ImmutableSet.Builder<Class<?>> classes = ImmutableSet.builder();
        private final ImmutableMap.Builder<String, Consumer<LinkedBindingBuilder<Icon>>> icons = ImmutableMap.builder();
        private final ImmutableSet.Builder<String> serverIcons = ImmutableSet.builder();
        private Optional<IdentityMapperBinding> identityMapperBinding = Optional.empty();
        private McpMetadata metadata = DEFAULT;
        private Mode mode = STANDARD;
        private Optional<Consumer<LinkedBindingBuilder<SessionController>>> sessionControllerBinding = Optional.empty();
        private Consumer<LinkedBindingBuilder<McpCancellationHandler>> cancellationHandlerBinding = binder -> binder.toInstance(McpCancellationHandler.DEFAULT);

        private Builder()
        {
        }

        public Builder withMetadata(McpMetadata metadata)
        {
            this.metadata = requireNonNull(metadata, "metadata is null");
            return this;
        }

        public Builder withAllInClass(Class<?> clazz)
        {
            classes.add(clazz);

            return this;
        }

        public Builder withMode(Mode mode)
        {
            this.mode = requireNonNull(mode, "mode is null");
            return this;
        }

        public <T> Builder withIdentityMapper(Class<T> identityType, Consumer<AnnotatedBindingBuilder<McpIdentityMapper>> identityMapperBinding)
        {
            checkArgument(this.identityMapperBinding.isEmpty(), "Identity mapper binding is already set");

            this.identityMapperBinding = Optional.of(new IdentityMapperBinding(identityType, identityMapperBinding));
            return this;
        }

        public Builder withSessions(Consumer<LinkedBindingBuilder<SessionController>> sessionControllerBinding)
        {
            checkArgument(this.sessionControllerBinding.isEmpty(), "Session controller binding is already set");

            this.sessionControllerBinding = Optional.of(sessionControllerBinding);
            return this;
        }

        public Builder withCancellationHandler(Consumer<LinkedBindingBuilder<McpCancellationHandler>> cancellationHandlerBinding)
        {
            checkState(sessionControllerBinding.isPresent(), "Session controller binding is required for cancellation support");

            this.cancellationHandlerBinding = requireNonNull(cancellationHandlerBinding, "cancellationHandlerBinding is null");
            return this;
        }

        public Builder addIcon(String name, Consumer<LinkedBindingBuilder<Icon>> binding)
        {
            icons.put(name, binding);
            return this;
        }

        // NOTE: icons must be bound via addIcon()
        public Builder withServerIcons(Collection<String> iconNames)
        {
            serverIcons.addAll(iconNames);
            return this;
        }

        public Module build()
        {
            Set<Class<?>> classesSet = classes.build();

            ImmutableSet.Builder<ToolHandlerProvider> tools = ImmutableSet.builder();
            ImmutableSet.Builder<PromptHandlerProvider> prompts = ImmutableSet.builder();
            ImmutableSet.Builder<ResourceHandlerProvider> resources = ImmutableSet.builder();
            ImmutableSet.Builder<ResourceTemplateHandlerProvider> resourceTemplates = ImmutableSet.builder();
            ImmutableSet.Builder<CompletionHandlerProvider> completions = ImmutableSet.builder();

            classesSet.forEach(clazz -> {
                Optional<? extends Class<?>> identityClass = identityMapperBinding.map(IdentityMapperBinding::identityType);

                forAllInClass(clazz, McpTool.class, identityClass, (mcpTool, method, parameters) ->
                        tools.add(new ToolHandlerProvider(mcpTool, clazz, method, parameters)));

                forAllInClass(clazz, McpPrompt.class, identityClass, (mcpPrompt, method, parameters) ->
                        prompts.add(new PromptHandlerProvider(mcpPrompt, clazz, method, parameters)));

                forAllInClass(clazz, McpResource.class, identityClass, (mcpResource, method, parameters) ->
                        resources.add(new ResourceHandlerProvider(mcpResource, clazz, method, parameters)));

                forAllInClass(clazz, McpResourceTemplate.class, identityClass, (mcpResourceTemplate, method, parameters) ->
                        resourceTemplates.add(new ResourceTemplateHandlerProvider(mcpResourceTemplate, clazz, method, parameters)));

                forAllInClass(clazz, McpPromptCompletion.class, identityClass, (mcpPromptCompletion, method, parameters) ->
                        completions.add(new CompletionHandlerProvider(mcpPromptCompletion, clazz, method, parameters)));
                forAllInClass(clazz, McpResourceTemplateCompletion.class, identityClass, (mcpResourceCompletion, method, parameters) ->
                        completions.add(new CompletionHandlerProvider(mcpResourceCompletion, clazz, method, parameters)));
            });

            Set<ToolHandlerProvider> localTools = tools.build();
            Set<PromptHandlerProvider> localPrompts = prompts.build();
            Set<ResourceHandlerProvider> localResources = resources.build();
            Set<ResourceTemplateHandlerProvider> localResourceTemplates = resourceTemplates.build();
            Set<CompletionHandlerProvider> localCompletions = completions.build();

            IdentityMapperBinding localIdentityMapperBinding = identityMapperBinding.orElseThrow(() -> new IllegalStateException("Identity mapper binding is required"));

            return new McpModule(
                    mode,
                    metadata,
                    localIdentityMapperBinding,
                    classesSet,
                    localTools,
                    localPrompts,
                    localResources,
                    localResourceTemplates,
                    localCompletions,
                    sessionControllerBinding,
                    cancellationHandlerBinding,
                    icons.build(),
                    serverIcons.build());
        }
    }

    @Override
    public void configure(Binder binder)
    {
        binder.bind(McpMetadata.class).toInstance(metadata);

        configBinder(binder).bindConfig(McpConfig.class);

        newOptionalBinder(binder, ErrorHandler.class);

        bindClasses(binder);
        bindTools(binder);
        bindPrompts(binder);
        bindResources(binder);
        bindResourceTemplates(binder);
        bindJsonSubTypes(binder);
        bindIdentityMapper(binder);
        bindCompletions(binder);
        bindSessions(binder);
        bindCancellation(binder);
        bindIcons(binder);

        if (mode == STANDARD) {
            binder.install(new InternalMcpModule());
        }
    }

    private void bindIdentityMapper(Binder binder)
    {
        identityMapperBinding.identityMapperBinding.accept(binder.bind(McpIdentityMapper.class));
        binder.bind(IdentityMapperMetadata.class).toInstance(new IdentityMapperMetadata(identityMapperBinding.identityType));
    }

    private void bindIcons(Binder binder)
    {
        binder.bind(IconHelper.class).in(SINGLETON);

        MapBinder<String, Icon> mapBinder = MapBinder.newMapBinder(binder, String.class, Icon.class);
        icons.forEach((name, binding) -> binding.accept(mapBinder.addBinding(name)));

        Multibinder<String> serverIconsBinder = newSetBinder(binder, String.class, Names.named(MCP_SERVER_ICONS));
        serverIcons.forEach(iconName -> serverIconsBinder.addBinding().toInstance(iconName));
    }

    private void bindCancellation(Binder binder)
    {
        binder.bind(CancellationController.class).in(SINGLETON);
        cancellationHandlerBinding.accept(binder.bind(McpCancellationHandler.class));
    }

    private void bindSessions(Binder binder)
    {
        OptionalBinder<SessionController> sessionControllerBinder = newOptionalBinder(binder, SessionController.class);
        sessionControllerBinding.ifPresent(sessionControllerBinding -> sessionControllerBinding.accept(sessionControllerBinder.setBinding()));
    }

    private void bindClasses(Binder binder)
    {
        classes.forEach(clazz -> binder.bind(clazz).in(SINGLETON));
    }

    private void bindResources(Binder binder)
    {
        Multibinder<ResourceEntry> resourcesBinder = newSetBinder(binder, ResourceEntry.class);
        resources.forEach(resource -> resourcesBinder.addBinding().toProvider(resource).in(SINGLETON));
    }

    private void bindResourceTemplates(Binder binder)
    {
        Multibinder<ResourceTemplateEntry> resourceTemplatesBinder = newSetBinder(binder, ResourceTemplateEntry.class);
        resourceTemplates.forEach(resourceTemplate -> resourceTemplatesBinder.addBinding().toProvider(resourceTemplate).in(SINGLETON));
    }

    private void bindCompletions(Binder binder)
    {
        Multibinder<CompletionEntry> completionsBinder = newSetBinder(binder, CompletionEntry.class);
        completions.forEach(completion -> completionsBinder.addBinding().toProvider(completion).in(SINGLETON));
    }

    private void bindPrompts(Binder binder)
    {
        Multibinder<PromptEntry> promptsBinder = newSetBinder(binder, PromptEntry.class);
        prompts.forEach(prompt -> promptsBinder.addBinding().toProvider(prompt).in(SINGLETON));
    }

    private void bindTools(Binder binder)
    {
        Multibinder<ToolEntry> toolsBinder = newSetBinder(binder, ToolEntry.class);
        tools.forEach(tool -> toolsBinder.addBinding().toProvider(tool).in(SINGLETON));
    }

    private void bindJsonSubTypes(Binder binder)
    {
        JsonSubType jsonSubType = buildJsonSubType();

        JsonSubTypeBinder jsonSubTypeBinder = jsonSubTypeBinder(binder);
        jsonSubTypeBinder.bindJsonSubType(jsonSubType);
    }
}

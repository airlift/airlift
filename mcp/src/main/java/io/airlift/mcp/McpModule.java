package io.airlift.mcp;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
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
import io.airlift.mcp.reflection.CompletionHandlerProvider;
import io.airlift.mcp.reflection.IdentityMapperMetadata;
import io.airlift.mcp.reflection.PromptHandlerProvider;
import io.airlift.mcp.reflection.ResourceHandlerProvider;
import io.airlift.mcp.reflection.ResourceTemplateHandlerProvider;
import io.airlift.mcp.reflection.ToolHandlerProvider;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.versions.ResourceVersionController;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;
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
    private final Mode mode;
    private final McpMetadata metadata;
    private final Optional<IdentityMapperBinding> identityMapperBinding;
    private final Set<Class<?>> classes;
    private final Set<ToolHandlerProvider> tools;
    private final Set<PromptHandlerProvider> prompts;
    private final Set<ResourceHandlerProvider> resources;
    private final Set<ResourceTemplateHandlerProvider> resourceTemplates;
    private final Set<CompletionHandlerProvider> completions;
    private final Optional<Consumer<LinkedBindingBuilder<SessionController>>> sessionControllerBinding;

    public static Builder builder()
    {
        return new Builder();
    }

    private McpModule(Mode mode, McpMetadata metadata, Optional<IdentityMapperBinding> identityMapperBinding, Set<Class<?>> classes, Set<ToolHandlerProvider> tools, Set<PromptHandlerProvider> prompts, Set<ResourceHandlerProvider> resources, Set<ResourceTemplateHandlerProvider> resourceTemplates, Set<CompletionHandlerProvider> completions, Optional<Consumer<LinkedBindingBuilder<SessionController>>> sessionControllerBinding)
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
    }

    public enum Mode
    {
        STANDARD,
        UNBOUND_IMPLEMENTATION,
    }

    record IdentityMapperBinding(Class<?> identityType, Consumer<? extends LinkedBindingBuilder<?>> identityMapperBinding)
    {
        IdentityMapperBinding
        {
            requireNonNull(identityType, "identityType is null");
            requireNonNull(identityMapperBinding, "identityMapperBinding is null");
        }
    }

    public static class Builder
    {
        private final ImmutableSet.Builder<Class<?>> classes = ImmutableSet.builder();
        private Optional<IdentityMapperBinding> identityMapperBinding = Optional.empty();
        private McpMetadata metadata = DEFAULT;
        private Mode mode = STANDARD;
        private Optional<Consumer<LinkedBindingBuilder<SessionController>>> sessionControllerBinding = Optional.empty();

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

        public <T> Builder withIdentityMapper(Class<T> identityType, Consumer<LinkedBindingBuilder<McpIdentityMapper>> identityMapperBinding)
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

            if (!localTools.isEmpty()) {
                metadata = metadata.withTools(true);
            }
            if (!localPrompts.isEmpty()) {
                metadata = metadata.withPrompts(true);
            }
            if (!localResources.isEmpty()) {
                metadata = metadata.withResources(true);
            }
            if (!localCompletions.isEmpty()) {
                metadata = metadata.withCompletions(true);
            }

            return new McpModule(mode, metadata, identityMapperBinding, classesSet, localTools, localPrompts, localResources, localResourceTemplates, localCompletions, sessionControllerBinding);
        }
    }

    @Override
    public void configure(Binder binder)
    {
        binder.bind(McpMetadata.class).toInstance(metadata);
        binder.bind(ResourceVersionController.class).in(SINGLETON);

        configBinder(binder).bindConfig(McpConfig.class);

        bindClasses(binder);
        bindTools(binder);
        bindPrompts(binder);
        bindResources(binder);
        bindResourceTemplates(binder);
        bindJsonSubTypes(binder);
        bindIdentityMapper(binder);
        bindCompletions(binder);
        bindSessions(binder);

        if (mode == STANDARD) {
            binder.install(new InternalMcpModule());
        }
    }

    private void bindSessions(Binder binder)
    {
        OptionalBinder<SessionController> sessionControllerBinder = newOptionalBinder(binder, SessionController.class);
        sessionControllerBinding.ifPresent(sessionControllerBinding -> sessionControllerBinding.accept(sessionControllerBinder.setBinding()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void bindIdentityMapper(Binder binder)
    {
        OptionalBinder<? extends McpIdentityMapper> identityBinder = newOptionalBinder(binder, new TypeLiteral<>() {});

        identityMapperBinding.ifPresent(binding -> {
            Consumer rawConsumer = binding.identityMapperBinding;
            rawConsumer.accept(identityBinder.setBinding());
            binder.bind(IdentityMapperMetadata.class).toInstance(new IdentityMapperMetadata(binding.identityType));
        });
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
        JsonSubTypeBinder jsonSubTypeBinder = jsonSubTypeBinder(binder);

        JsonSubType contentJsonSubType = JsonSubType.builder()
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
        jsonSubTypeBinder.bindJsonSubType(contentJsonSubType);
    }
}

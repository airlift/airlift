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
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.ResourceEntry;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.model.Content;
import io.airlift.mcp.model.Content.AudioContent;
import io.airlift.mcp.model.Content.EmbeddedResource;
import io.airlift.mcp.model.Content.ImageContent;
import io.airlift.mcp.model.Content.ResourceLink;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.Role;
import io.airlift.mcp.reference.ReferenceModule;
import io.airlift.mcp.reflection.IdentityMapperMetadata;
import io.airlift.mcp.reflection.PromptHandlerProvider;
import io.airlift.mcp.reflection.ResourceHandlerProvider;
import io.airlift.mcp.reflection.ToolHandlerProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.json.JsonSubTypeBinder.jsonSubTypeBinder;
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

    public static Builder builder()
    {
        return new Builder();
    }

    private McpModule(Mode mode, McpMetadata metadata, Optional<IdentityMapperBinding> identityMapperBinding, Set<Class<?>> classes, Set<ToolHandlerProvider> tools, Set<PromptHandlerProvider> prompts, Set<ResourceHandlerProvider> resources)
    {
        this.mode = requireNonNull(mode, "mode is null");
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.identityMapperBinding = requireNonNull(identityMapperBinding, "identityMapperBinding is null");
        this.classes = ImmutableSet.copyOf(classes);
        this.tools = ImmutableSet.copyOf(tools);
        this.prompts = ImmutableSet.copyOf(prompts);
        this.resources = ImmutableSet.copyOf(resources);

        validateRoles();
    }

    public enum Mode
    {
        REFERENCE_SDK,
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
        private McpMetadata metadata = new McpMetadata("/mcp");
        private Mode mode = Mode.REFERENCE_SDK;

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

        public <T> Builder withIdentityMapper(Class<T> identityType, Consumer<LinkedBindingBuilder<McpIdentityMapper<T>>> identityMapperBinding)
        {
            this.identityMapperBinding = Optional.of(new IdentityMapperBinding(identityType, identityMapperBinding));
            return this;
        }

        public Module build()
        {
            Set<Class<?>> classesSet = classes.build();

            ImmutableSet.Builder<ToolHandlerProvider> tools = ImmutableSet.builder();
            ImmutableSet.Builder<PromptHandlerProvider> prompts = ImmutableSet.builder();
            ImmutableSet.Builder<ResourceHandlerProvider> resources = ImmutableSet.builder();

            classesSet.forEach(clazz -> {
                Optional<? extends Class<?>> identityClass = identityMapperBinding.map(IdentityMapperBinding::identityType);

                forAllInClass(clazz, McpTool.class, identityClass, (mcpTool, method, parameters) ->
                        tools.add(new ToolHandlerProvider(mcpTool, clazz, method, parameters)));

                forAllInClass(clazz, McpPrompt.class, identityClass, (mcpPrompt, method, parameters) ->
                        prompts.add(new PromptHandlerProvider(mcpPrompt, clazz, method, parameters)));

                forAllInClass(clazz, McpResource.class, identityClass, (mcpResource, method, parameters) ->
                        resources.add(new ResourceHandlerProvider(mcpResource, clazz, method, parameters)));
            });

            Set<ToolHandlerProvider> localTools = tools.build();
            Set<PromptHandlerProvider> localPrompts = prompts.build();
            Set<ResourceHandlerProvider> localResources = resources.build();

            if (!localTools.isEmpty()) {
                metadata = metadata.withTools(true);
            }
            if (!localPrompts.isEmpty()) {
                metadata = metadata.withPrompts(true);
            }
            if (!localResources.isEmpty()) {
                metadata = metadata.withResources(true);
            }

            return new McpModule(mode, metadata, identityMapperBinding, classesSet, localTools, localPrompts, localResources);
        }
    }

    @Override
    public void configure(Binder binder)
    {
        binder.bind(McpMetadata.class).toInstance(metadata);

        bindClasses(binder);
        bindTools(binder);
        bindPrompts(binder);
        bindResources(binder);
        bindJsonSubTypes(binder);
        bindIdentityMapper(binder);

        if (mode == Mode.REFERENCE_SDK) {
            binder.install(new ReferenceModule());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void bindIdentityMapper(Binder binder)
    {
        OptionalBinder<? extends McpIdentityMapper<?>> identityBinder = OptionalBinder.newOptionalBinder(binder, new TypeLiteral<>() {});

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
                .build();
        jsonSubTypeBinder.bindJsonSubType(contentJsonSubType);
    }

    private void validateRoles()
    {
        Set<String> ourRoles = Stream.of(Role.values()).map(role -> role.name().toUpperCase(Locale.ROOT)).collect(toImmutableSet());
        Set<String> theirRoles = Stream.of(McpSchema.Role.values()).map(role -> role.name().toUpperCase(Locale.ROOT)).collect(toImmutableSet());

        checkState(ourRoles.equals(theirRoles), "Roles in McpModule do not match the roles defined in McpSchema: Ours: %s, Theirs: %s: ", ourRoles, theirRoles);
    }
}

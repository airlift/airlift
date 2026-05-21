package io.airlift.mcp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Names;
import com.google.inject.util.Types;
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
import io.airlift.mcp.model.JsonRpcMessage;
import io.airlift.mcp.model.JsonRpcMessageDeserializer;
import io.airlift.mcp.model.JsonSchemaBuilder;
import io.airlift.mcp.model.JsonSchemaBuilder.DefaultSchemaBuilderProvider;
import io.airlift.mcp.model.JsonSchemaBuilder.SchemaBuilder;
import io.airlift.mcp.operations.LegacyOperations;
import io.airlift.mcp.operations.Operations;
import io.airlift.mcp.operations.OperationsModule;
import io.airlift.mcp.operations.SessionlessOperations;
import io.airlift.mcp.reflection.AppContent;
import io.airlift.mcp.reflection.CompletionHandlerProvider;
import io.airlift.mcp.reflection.IconHelper;
import io.airlift.mcp.reflection.IdentityMapperMetadata;
import io.airlift.mcp.reflection.PromptHandlerProvider;
import io.airlift.mcp.reflection.ReflectionHelper;
import io.airlift.mcp.reflection.ResourceHandlerProvider;
import io.airlift.mcp.reflection.ResourceTemplateHandlerProvider;
import io.airlift.mcp.reflection.ToolHandlerProvider;
import io.airlift.mcp.sessions.CachingSessionController;
import io.airlift.mcp.sessions.ForSessionCaching;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.storage.StorageController;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashMap;
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
import static io.airlift.json.JsonBinder.jsonBinder;
import static io.airlift.json.JsonSubTypeBinder.jsonSubTypeBinder;
import static io.airlift.mcp.reflection.ReflectionHelper.forAllInClass;
import static io.airlift.mcp.reflection.SkillsHelper.resourceFromSkill;
import static io.airlift.mcp.reflection.SkillsHelper.resourceTemplateFromSkillTemplate;
import static java.util.Objects.requireNonNull;

public class McpModule
        implements Module
{
    public static final String MCP_SERVER_ICONS = "mcp-server-icons";

    private final Consumer<LinkedBindingBuilder<McpMetadataMapper>> metadataBinding;
    private final IdentityMapperBinding identityMapperBinding;
    private final Set<Class<?>> classes;
    private final Set<Provider<ToolEntry>> tools;
    private final Set<Provider<PromptEntry>> prompts;
    private final Set<Provider<ResourceEntry>> resources;
    private final Set<Provider<ResourceTemplateEntry>> resourceTemplates;
    private final Set<Provider<CompletionEntry>> completions;
    private final Optional<Consumer<LinkedBindingBuilder<SessionController>>> sessionControllerBinding;
    private final Optional<Consumer<LinkedBindingBuilder<McpCapabilityFilter>>> capabilityFilterBinding;
    private final Map<String, Consumer<LinkedBindingBuilder<Icon>>> icons;
    private final Set<String> serverIcons;
    private final Optional<Consumer<LinkedBindingBuilder<StorageController>>> storageControllerBinding;
    private final Consumer<LinkedBindingBuilder<Operations>> operationsBinding;
    private final Optional<Class<? extends Annotation>> filterBindingAnnotation;
    private final Consumer<LinkedBindingBuilder<SchemaBuilder>> schemaBuilderBinding;

    public static Builder builder()
    {
        return new Builder();
    }

    private McpModule(
            Consumer<LinkedBindingBuilder<McpMetadataMapper>> metadataBinding,
            IdentityMapperBinding identityMapperBinding,
            Set<Class<?>> classes,
            Set<Provider<ToolEntry>> tools,
            Set<Provider<PromptEntry>> prompts,
            Set<Provider<ResourceEntry>> resources,
            Set<Provider<ResourceTemplateEntry>> resourceTemplates,
            Set<Provider<CompletionEntry>> completions,
            Optional<Consumer<LinkedBindingBuilder<SessionController>>> sessionControllerBinding,
            Optional<Consumer<LinkedBindingBuilder<McpCapabilityFilter>>> capabilityFilterBinding,
            Map<String, Consumer<LinkedBindingBuilder<Icon>>> icons,
            Set<String> serverIcons,
            Optional<Consumer<LinkedBindingBuilder<StorageController>>> storageControllerBinding,
            Consumer<LinkedBindingBuilder<Operations>> operationsBinding,
            Optional<Class<? extends Annotation>> filterBindingAnnotation,
            Consumer<LinkedBindingBuilder<SchemaBuilder>> schemaBuilderBinding)
    {
        this.metadataBinding = requireNonNull(metadataBinding, "metadataBinding is null");
        this.identityMapperBinding = requireNonNull(identityMapperBinding, "identityMapperBinding is null");
        this.classes = ImmutableSet.copyOf(classes);
        this.tools = ImmutableSet.copyOf(tools);
        this.prompts = ImmutableSet.copyOf(prompts);
        this.resources = ImmutableSet.copyOf(resources);
        this.resourceTemplates = ImmutableSet.copyOf(resourceTemplates);
        this.completions = ImmutableSet.copyOf(completions);
        this.sessionControllerBinding = requireNonNull(sessionControllerBinding, "sessionControllerBinding is null");
        this.capabilityFilterBinding = requireNonNull(capabilityFilterBinding, "capabilityFilterBinding is null");
        this.icons = ImmutableMap.copyOf(icons);
        this.serverIcons = ImmutableSet.copyOf(serverIcons);
        this.storageControllerBinding = requireNonNull(storageControllerBinding, "storageControllerBinding is null");
        this.operationsBinding = requireNonNull(operationsBinding, "operationsBinding is null");
        this.filterBindingAnnotation = requireNonNull(filterBindingAnnotation, "filterBindingAnnotation is null");
        this.schemaBuilderBinding = requireNonNull(schemaBuilderBinding, "schemaBuilderBinding is null");
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
        private Optional<Consumer<LinkedBindingBuilder<McpMetadataMapper>>> metadataBinding = Optional.empty();
        private Optional<Consumer<LinkedBindingBuilder<SessionController>>> sessionControllerBinding = Optional.empty();
        private Optional<Consumer<LinkedBindingBuilder<McpCapabilityFilter>>> capabilityFilterBinding = Optional.empty();
        private Optional<Consumer<LinkedBindingBuilder<StorageController>>> storageControllerBinding = Optional.empty();
        private Consumer<LinkedBindingBuilder<Operations>> operationsBinding = binding -> binding.to(SessionlessOperations.class).in(SINGLETON);
        private Optional<Class<? extends Annotation>> filterBindingAnnotation = Optional.empty();
        private Optional<Consumer<LinkedBindingBuilder<SchemaBuilder>>> schemaBuilderBinding = Optional.empty();

        private Builder() {}

        public Builder withAllInClass(Class<?> clazz)
        {
            classes.add(clazz);

            return this;
        }

        public Builder withMetadata(Consumer<LinkedBindingBuilder<McpMetadataMapper>> metadataBinding)
        {
            checkArgument(this.metadataBinding.isEmpty(), "Metadata binding is already set");

            this.metadataBinding = Optional.of(metadataBinding);
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
            checkState(storageControllerBinding.isPresent(), "Storage controller binding is required for session support");
            checkArgument(this.sessionControllerBinding.isEmpty(), "Session controller binding is already set");

            this.sessionControllerBinding = Optional.of(sessionControllerBinding);
            operationsBinding = binding -> binding.to(LegacyOperations.class).in(SINGLETON);
            return this;
        }

        public Builder withCapabilityFilter(Consumer<LinkedBindingBuilder<McpCapabilityFilter>> filterBinding)
        {
            checkArgument(this.capabilityFilterBinding.isEmpty(), "Capability filter binding is already set");
            this.capabilityFilterBinding = Optional.of(filterBinding);
            return this;
        }

        public Builder withHttpServerBinding(Class<? extends Annotation> annotation)
        {
            checkState(filterBindingAnnotation.isEmpty(), "HTTP server binding annotation is already set");
            filterBindingAnnotation = Optional.of(requireNonNull(annotation, "annotation is null"));
            return this;
        }

        public Builder withStorage(Consumer<LinkedBindingBuilder<StorageController>> storageControllerBinding)
        {
            checkArgument(this.storageControllerBinding.isEmpty(), "Storage controller binding is already set");

            this.storageControllerBinding = Optional.of(storageControllerBinding);
            return this;
        }

        public Builder withSchemaBuilder(Consumer<LinkedBindingBuilder<SchemaBuilder>> schemaBuilderBinding)
        {
            checkArgument(this.schemaBuilderBinding.isEmpty(), "Schema builder binding is already set");

            this.schemaBuilderBinding = Optional.of(schemaBuilderBinding);
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

            Map<String, AppContent> apps = new HashMap<>();

            ImmutableSet.Builder<Provider<ToolEntry>> tools = ImmutableSet.builder();
            ImmutableSet.Builder<Provider<PromptEntry>> prompts = ImmutableSet.builder();
            ImmutableSet.Builder<Provider<ResourceEntry>> resources = ImmutableSet.builder();
            ImmutableSet.Builder<Provider<ResourceTemplateEntry>> resourceTemplates = ImmutableSet.builder();
            ImmutableSet.Builder<Provider<CompletionEntry>> completions = ImmutableSet.builder();

            Optional<? extends Class<?>> identityClass = identityMapperBinding.map(IdentityMapperBinding::identityType);

            classesSet.forEach(clazz -> {
                forAllInClass(clazz, McpTool.class, identityClass, (mcpTool, method, parameters) ->
                        tools.add(new ToolHandlerProvider(mcpTool, clazz, method, parameters, apps, resources::add)));

                forAllInClass(clazz, McpPrompt.class, identityClass, (mcpPrompt, method, parameters) ->
                        prompts.add(new PromptHandlerProvider(mcpPrompt, clazz, method, parameters)));

                forAllInClass(clazz, McpResource.class, identityClass, (mcpResource, method, parameters) ->
                        resources.add(new ResourceHandlerProvider(mcpResource, clazz, method, parameters, false)));
                forAllInClass(clazz, McpResourceTemplate.class, identityClass, (mcpResourceTemplate, method, parameters) ->
                        resourceTemplates.add(new ResourceTemplateHandlerProvider(mcpResourceTemplate, clazz, method, parameters, false)));

                forAllInClass(clazz, McpPromptCompletion.class, identityClass, (mcpPromptCompletion, method, parameters) ->
                        completions.add(new CompletionHandlerProvider(mcpPromptCompletion, clazz, method, parameters)));
                forAllInClass(clazz, McpResourceTemplateCompletion.class, identityClass, (mcpResourceCompletion, method, parameters) ->
                        completions.add(new CompletionHandlerProvider(mcpResourceCompletion, clazz, method, parameters)));

                forAllInClass(clazz, McpSkill.class, identityClass, (mcpSkill, method, parameters) ->
                        resources.add(new ResourceHandlerProvider(resourceFromSkill(mcpSkill), clazz, method, parameters, true)));
                forAllInClass(clazz, McpSkillTemplate.class, identityClass, (mcpSkillTemplate, method, parameters) ->
                        resourceTemplates.add(new ResourceTemplateHandlerProvider(resourceTemplateFromSkillTemplate(mcpSkillTemplate), clazz, method, parameters, true)));
            });

            Set<Provider<ToolEntry>> localTools = tools.build();
            Set<Provider<PromptEntry>> localPrompts = prompts.build();
            Set<Provider<ResourceEntry>> localResources = resources.build();
            Set<Provider<ResourceTemplateEntry>> localResourceTemplates = resourceTemplates.build();
            Set<Provider<CompletionEntry>> localCompletions = completions.build();

            IdentityMapperBinding localIdentityMapperBinding = identityMapperBinding.orElseThrow(() -> new IllegalStateException("Identity mapper binding is required"));
            Consumer<LinkedBindingBuilder<McpMetadataMapper>> localMetadataBinding = metadataBinding.orElseGet(() -> binding -> binding.toInstance(_ -> McpMetadata.DEFAULT));
            Consumer<LinkedBindingBuilder<SchemaBuilder>> localSchemaBuilderBinding = schemaBuilderBinding.orElseGet(() -> binding -> binding.toProvider(DefaultSchemaBuilderProvider.class));

            return new McpModule(
                    localMetadataBinding,
                    localIdentityMapperBinding,
                    classesSet,
                    localTools,
                    localPrompts,
                    localResources,
                    localResourceTemplates,
                    localCompletions,
                    sessionControllerBinding,
                    capabilityFilterBinding,
                    icons.build(),
                    serverIcons.build(),
                    storageControllerBinding,
                    operationsBinding,
                    filterBindingAnnotation,
                    localSchemaBuilderBinding);
        }
    }

    @Override
    public void configure(Binder binder)
    {
        metadataBinding.accept(binder.bind(McpMetadataMapper.class));
        storageControllerBinding.ifPresent(binding -> binding.accept(binder.bind(StorageController.class)));
        operationsBinding.accept(binder.bind(Operations.class));

        configBinder(binder).bindConfig(McpConfig.class);

        jsonBinder(binder)
                .addDeserializerBinding(JsonRpcMessage.class)
                .to(JsonRpcMessageDeserializer.class);

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
        bindCapabilityFilter(binder);
        bindIcons(binder);
        bindSchemaBuilder(binder);

        binder.install(new InternalMcpModule(filterBindingAnnotation));
        binder.install(new OperationsModule());
    }

    private void bindSchemaBuilder(Binder binder)
    {
        schemaBuilderBinding.accept(binder.bind(SchemaBuilder.class));
        binder.bind(JsonSchemaBuilder.class).in(SINGLETON);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void bindIdentityMapper(Binder binder)
    {
        identityMapperBinding.identityMapperBinding.accept(binder.bind(McpIdentityMapper.class));
        binder.bind(IdentityMapperMetadata.class).toInstance(new IdentityMapperMetadata(identityMapperBinding.identityType));

        ParameterizedType identitySupplierType = Types.newParameterizedType(McpIdentitySupplier.class, identityMapperBinding.identityType);
        TypeLiteral identitySupplierTypeLiteral = TypeLiteral.get(identitySupplierType);
        McpIdentitySupplier<?> supplier = request -> identityMapperBinding.identityType.cast(ReflectionHelper.retrieveIdentityValue(request));
        binder.bind(identitySupplierTypeLiteral).toInstance(supplier);
    }

    private void bindIcons(Binder binder)
    {
        binder.bind(IconHelper.class).in(SINGLETON);

        MapBinder<String, Icon> mapBinder = MapBinder.newMapBinder(binder, String.class, Icon.class);
        icons.forEach((name, binding) -> binding.accept(mapBinder.addBinding(name)));

        Multibinder<String> serverIconsBinder = newSetBinder(binder, String.class, Names.named(MCP_SERVER_ICONS));
        serverIcons.forEach(iconName -> serverIconsBinder.addBinding().toInstance(iconName));
    }

    private void bindSessions(Binder binder)
    {
        OptionalBinder<SessionController> sessionControllerBinder = newOptionalBinder(binder, SessionController.class);
        OptionalBinder<SessionController> sessionControllerDelegateBinder = newOptionalBinder(binder, Key.get(SessionController.class, ForSessionCaching.class));

        sessionControllerBinding.ifPresent(sessionControllerBinding -> {
            sessionControllerBinding.accept(sessionControllerDelegateBinder.setBinding());
            sessionControllerBinder.setDefault().to(CachingSessionController.class).in(SINGLETON);
        });
    }

    private void bindCapabilityFilter(Binder binder)
    {
        OptionalBinder<McpCapabilityFilter> filterBinder = newOptionalBinder(binder, McpCapabilityFilter.class);
        filterBinder.setDefault().to(AllowAllCapabilityFilter.class).in(SINGLETON);
        capabilityFilterBinding.ifPresent(binding -> binding.accept(filterBinder.setBinding()));
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

package io.airlift.api.openapi;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import io.airlift.api.ApiEnumValueResolver;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.model.ModelServices;
import io.airlift.jaxrs.JaxrsBinder;
import org.glassfish.jersey.server.model.Resource;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.api.binding.ApiBinders.newSetBinder;
import static io.airlift.api.binding.ApiBindingKeys.annotatedKey;
import static io.airlift.api.openapi.OpenApiBuilder.JsonUriMode.TEMPLATE;
import static io.airlift.api.openapi.OpenApiBuilder.jsonUriBuilder;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static java.util.Objects.requireNonNull;

public class OpenApiModule
        implements Module
{
    private final ModelServices modelServices;
    private final Optional<Class<? extends Annotation>> bindingAnnotation;
    private final OpenApiMetadata metadata;
    private final Optional<OpenApiSecurityMetadata> securityMetadata;
    private final Consumer<LinkedBindingBuilder<OpenApiFilter>> openApiFilterProviderBinding;
    private final List<Consumer<LinkedBindingBuilder<OpenApiExtensionFilter>>> extensionFilterBindings;
    private final ApiEnumValueResolver enumValueResolver;

    public OpenApiModule(
            ModelServices modelServices,
            Optional<Class<? extends Annotation>> bindingAnnotation,
            OpenApiMetadata metadata,
            Consumer<LinkedBindingBuilder<OpenApiFilter>> openApiFilterProviderBinding,
            List<Consumer<LinkedBindingBuilder<OpenApiExtensionFilter>>> extensionFilterBindings,
            ApiEnumValueResolver enumValueResolver)
    {
        this(modelServices, bindingAnnotation, metadata, Optional.empty(), openApiFilterProviderBinding, extensionFilterBindings, enumValueResolver);
    }

    public OpenApiModule(
            ModelServices modelServices,
            Optional<Class<? extends Annotation>> bindingAnnotation,
            OpenApiMetadata metadata,
            Optional<OpenApiSecurityMetadata> securityMetadata,
            Consumer<LinkedBindingBuilder<OpenApiFilter>> openApiFilterProviderBinding,
            List<Consumer<LinkedBindingBuilder<OpenApiExtensionFilter>>> extensionFilterBindings,
            ApiEnumValueResolver enumValueResolver)
    {
        this.modelServices = requireNonNull(modelServices, "modelServices is null");
        this.bindingAnnotation = requireNonNull(bindingAnnotation, "bindingAnnotation is null");
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.securityMetadata = requireNonNull(securityMetadata, "securityMetadata is null");
        this.openApiFilterProviderBinding = requireNonNull(openApiFilterProviderBinding, "openApiFilterProviderBinding is null");
        this.extensionFilterBindings = ImmutableList.copyOf(extensionFilterBindings);
        this.enumValueResolver = requireNonNull(enumValueResolver, "enumValueResolver is null");
    }

    @Override
    public void configure(Binder binder)
    {
        Map<ModelServiceType, List<ModelService>> servicesByType = modelServices.services().stream().collect(Collectors.groupingBy(modelService -> modelService.service().type()));

        binder.bind(annotatedKey(new TypeLiteral<Collection<ModelServiceType>>() {}, bindingAnnotation)).toInstance(servicesByType.keySet());

        Multibinder<OpenApiExtensionFilter> extensionFilterBinder = newSetBinder(binder, TypeLiteral.get(OpenApiExtensionFilter.class), bindingAnnotation);
        extensionFilterBindings.forEach(extensionFilterBinding -> extensionFilterBinding.accept(extensionFilterBinder.addBinding()));

        newOptionalBinder(binder, annotatedKey(OpenApiProvider.class, bindingAnnotation)).setBinding()
                .toProvider(new OpenApiProviderProvider(bindingAnnotation, modelServices, metadata, securityMetadata, enumValueResolver));

        openApiFilterProviderBinding.accept(binder.bind(annotatedKey(OpenApiFilter.class, bindingAnnotation)));

        binder.bind(annotatedKey(OpenApiMetadata.class, bindingAnnotation)).toInstance(metadata);

        JaxrsBinder jaxrsBinder = jaxrsBinder(binder, bindingAnnotation);
        jaxrsBinder.bindInstance(buildOpenApiResource(metadata));
        jaxrsBinder.bind(OpenApiResource.class, new OpenApiResourceProvider(bindingAnnotation));
    }

    private static Resource buildOpenApiResource(OpenApiMetadata metadata)
    {
        String adjustedPath = jsonUriBuilder(metadata, "unused", 0, TEMPLATE);

        return Resource.builder(OpenApiResource.class)
                .path(adjustedPath)
                .build();
    }

    private static class OpenApiProviderProvider
            implements Provider<OpenApiProvider>
    {
        private final Optional<Class<? extends Annotation>> bindingAnnotation;
        private final ModelServices modelServices;
        private final OpenApiMetadata metadata;
        private final Optional<OpenApiSecurityMetadata> securityMetadata;
        private final ApiEnumValueResolver enumValueResolver;
        private Injector injector;

        private OpenApiProviderProvider(
                Optional<Class<? extends Annotation>> bindingAnnotation,
                ModelServices modelServices,
                OpenApiMetadata metadata,
                Optional<OpenApiSecurityMetadata> securityMetadata,
                ApiEnumValueResolver enumValueResolver)
        {
            this.bindingAnnotation = requireNonNull(bindingAnnotation, "bindingAnnotation is null");
            this.modelServices = requireNonNull(modelServices, "modelServices is null");
            this.metadata = requireNonNull(metadata, "metadata is null");
            this.securityMetadata = requireNonNull(securityMetadata, "securityMetadata is null");
            this.enumValueResolver = requireNonNull(enumValueResolver, "enumValueResolver is null");
        }

        @Inject
        public void setInjector(Injector injector)
        {
            this.injector = requireNonNull(injector, "injector is null");
        }

        @Override
        public OpenApiProvider get()
        {
            Set<OpenApiExtensionFilter> extensionFilters = injector.getInstance(annotatedKey(new TypeLiteral<Set<OpenApiExtensionFilter>>() {}, bindingAnnotation));
            return OpenApiProvider.create(modelServices, metadata, securityMetadata, ImmutableList.copyOf(extensionFilters), enumValueResolver);
        }
    }

    private static class OpenApiResourceProvider
            implements Provider<OpenApiResource>
    {
        private final Optional<Class<? extends Annotation>> bindingAnnotation;
        private Injector injector;

        private OpenApiResourceProvider(Optional<Class<? extends Annotation>> bindingAnnotation)
        {
            this.bindingAnnotation = requireNonNull(bindingAnnotation, "bindingAnnotation is null");
        }

        @Inject
        public void setInjector(Injector injector)
        {
            this.injector = requireNonNull(injector, "injector is null");
        }

        @Override
        public OpenApiResource get()
        {
            return new OpenApiResource(
                    injector.getInstance(annotatedKey(new TypeLiteral<Collection<ModelServiceType>>() {}, bindingAnnotation)),
                    injector.getInstance(annotatedKey(OpenApiProvider.class, bindingAnnotation)),
                    injector.getInstance(annotatedKey(OpenApiFilter.class, bindingAnnotation)),
                    injector.getInstance(annotatedKey(OpenApiMetadata.class, bindingAnnotation)));
        }
    }
}

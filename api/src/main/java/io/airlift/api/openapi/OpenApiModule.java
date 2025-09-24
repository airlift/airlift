package io.airlift.api.openapi;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.model.ModelServices;
import io.airlift.jaxrs.JaxrsBinder;
import org.glassfish.jersey.server.model.Resource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.api.openapi.OpenApiBuilder.JsonUriMode.TEMPLATE;
import static io.airlift.api.openapi.OpenApiBuilder.jsonUriBuilder;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static java.util.Objects.requireNonNull;

public class OpenApiModule
        implements Module
{
    private final ModelServices modelServices;
    private final OpenApiMetadata metadata;
    private final Consumer<AnnotatedBindingBuilder<OpenApiFilter>> openApiFilterProviderBinding;
    private final OpenApiExtensionFilter extensionFilter;

    public OpenApiModule(ModelServices modelServices, OpenApiMetadata metadata, Consumer<AnnotatedBindingBuilder<OpenApiFilter>> openApiFilterProviderBinding, OpenApiExtensionFilter extensionFilter)
    {
        this.modelServices = requireNonNull(modelServices, "modelServices is null");
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.openApiFilterProviderBinding = requireNonNull(openApiFilterProviderBinding, "openApiFilterProviderBinding is null");
        this.extensionFilter = requireNonNull(extensionFilter, "extensionFilter is null");
    }

    @Override
    public void configure(Binder binder)
    {
        Map<ModelServiceType, List<ModelService>> servicesByType = modelServices.services().stream().collect(Collectors.groupingBy(modelService -> modelService.service().type()));

        binder.bind(new TypeLiteral<Collection<ModelServiceType>>() {}).toInstance(servicesByType.keySet());

        newOptionalBinder(binder, OpenApiProvider.class).setBinding().toInstance((serviceType, methodFilter) -> {
            OpenApiBuilder builder = OpenApiBuilder.builder(serviceType, modelServices.deprecations(), metadata, methodFilter, extensionFilter);
            servicesByType.getOrDefault(serviceType, ImmutableList.of()).forEach(builder::addService);
            return builder.build();
        });

        openApiFilterProviderBinding.accept(binder.bind(new TypeLiteral<>() {}));

        binder.bind(OpenApiMetadata.class).toInstance(metadata);

        JaxrsBinder jaxrsBinder = jaxrsBinder(binder);
        jaxrsBinder.bindInstance(buildOpenApiResource(metadata));
        jaxrsBinder.bind(OpenApiResource.class);
    }

    private static Resource buildOpenApiResource(OpenApiMetadata metadata)
    {
        String adjustedPath = jsonUriBuilder(metadata, "unused", 0, TEMPLATE);

        return Resource.builder(OpenApiResource.class)
                .path(adjustedPath)
                .build();
    }
}

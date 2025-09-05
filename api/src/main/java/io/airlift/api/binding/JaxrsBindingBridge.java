package io.airlift.api.binding;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.openapi.OpenApiFilter;
import io.airlift.api.openapi.OpenApiMetadata;
import io.airlift.api.openapi.OpenApiProvider;
import jakarta.ws.rs.core.GenericType;
import org.glassfish.jersey.internal.inject.AbstractBinder;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

class JaxrsBindingBridge
        extends AbstractBinder
{
    private final Map<ModelService, Object> modelsMap;
    private final Collection<ModelServiceType> modelServiceTypes;
    private final Optional<OpenApiProvider> openApiProvider;
    private final Optional<OpenApiFilter> openApiFilter;
    private final Optional<OpenApiMetadata> openApiMetadata;

    @Inject
    JaxrsBindingBridge(
            Map<ModelService, Object> modelsMap,
            Set<ModelServiceType> modelServiceTypes,
            Optional<OpenApiProvider> openApiProvider,
            Optional<OpenApiFilter> openApiFilter,
            Optional<OpenApiMetadata> openApiMetadata)
    {
        this.modelsMap = ImmutableMap.copyOf(modelsMap);
        this.modelServiceTypes = ImmutableSet.copyOf(modelServiceTypes);
        this.openApiProvider = requireNonNull(openApiProvider, "openApiFilterProvider is null");
        this.openApiFilter = requireNonNull(openApiFilter, "openApiFilter is null");
        this.openApiMetadata = requireNonNull(openApiMetadata, "openApiMetadata is null");
    }

    @Override
    protected void configure()
    {
        modelsMap.forEach((service, instance) -> bind(instance).to(service.serviceClass()).in(Singleton.class));
        bind(modelServiceTypes).to(new GenericType<>() {}).in(Singleton.class);

        openApiProvider.ifPresent(provider -> bind(provider).to(OpenApiProvider.class).in(Singleton.class));
        openApiFilter.ifPresent(filter -> bind(filter).to(OpenApiFilter.class).in(Singleton.class));
        openApiMetadata.ifPresent(metadata -> bind(metadata).to(OpenApiMetadata.class).in(Singleton.class));
    }
}

package io.airlift.api.openapi;

import com.google.common.collect.ImmutableList;
import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiEnumValueResolver;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.model.ModelServices;
import io.airlift.api.openapi.models.OpenAPI;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public interface OpenApiProvider
{
    OpenAPI build(ModelServiceType serviceType, Predicate<Method> methodFilter);

    static OpenApiProvider create(ModelServices modelServices, OpenApiMetadata metadata, ApiBuilderConfig config)
    {
        return create(modelServices, metadata, Optional.empty(), ImmutableList.of(), requireNonNull(config, "config is null").enumValueResolver());
    }

    static OpenApiProvider create(ModelServices modelServices, OpenApiMetadata metadata, OpenApiSecurityMetadata securityMetadata, ApiBuilderConfig config)
    {
        return create(modelServices, metadata, Optional.of(securityMetadata), ImmutableList.of(), requireNonNull(config, "config is null").enumValueResolver());
    }

    static OpenApiProvider create(ModelServices modelServices, OpenApiMetadata metadata, List<OpenApiExtensionFilter> extensionFilters, ApiBuilderConfig config)
    {
        return create(modelServices, metadata, Optional.empty(), extensionFilters, requireNonNull(config, "config is null").enumValueResolver());
    }

    static OpenApiProvider create(
            ModelServices modelServices,
            OpenApiMetadata metadata,
            Optional<OpenApiSecurityMetadata> securityMetadata,
            List<OpenApiExtensionFilter> extensionFilters,
            ApiEnumValueResolver enumValueResolver)
    {
        List<OpenApiExtensionFilter> filters = ImmutableList.copyOf(extensionFilters);

        Map<ModelServiceType, List<ModelService>> servicesByType = modelServices.services().stream()
                .collect(Collectors.groupingBy(modelService -> modelService.service().type()));

        return (serviceType, methodFilter) -> {
            OpenApiBuilder builder = OpenApiBuilder.builder(serviceType, modelServices.deprecations(), metadata, securityMetadata, methodFilter, filters, enumValueResolver);
            servicesByType.getOrDefault(serviceType, ImmutableList.of()).forEach(builder::addService);
            return builder.build();
        };
    }
}

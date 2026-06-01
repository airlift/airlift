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
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public interface OpenApiProvider
{
    OpenAPI build(ModelServiceType serviceType, Predicate<Method> methodFilter);

    static OpenApiProvider create(ModelServices modelServices, OpenApiMetadata metadata, ApiBuilderConfig config)
    {
        ApiEnumValueResolver enumValueResolver = requireNonNull(config, "config is null").enumValueResolver();

        Map<ModelServiceType, List<ModelService>> servicesByType = modelServices.services().stream()
                .collect(Collectors.groupingBy(modelService -> modelService.service().type()));

        return (serviceType, methodFilter) -> {
            OpenApiBuilder builder = OpenApiBuilder.builder(serviceType, modelServices.deprecations(), metadata, methodFilter, (_, _, operation) -> operation, enumValueResolver);
            servicesByType.getOrDefault(serviceType, ImmutableList.of()).forEach(builder::addService);
            return builder.build();
        };
    }
}

package io.airlift.api.openapi;

import com.google.common.collect.ImmutableList;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.model.ModelServices;
import io.airlift.api.openapi.models.OpenAPI;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface OpenApiProvider
{
    OpenAPI build(ModelServiceType serviceType, Predicate<Method> methodFilter);

    static OpenApiProvider create(ModelServices modelServices, OpenApiMetadata metadata)
    {
        return create(modelServices, metadata, (_, _, operation) -> operation);
    }

    static OpenApiProvider create(ModelServices modelServices, OpenApiMetadata metadata, OpenApiExtensionFilter extensionFilter)
    {
        Map<ModelServiceType, List<ModelService>> servicesByType = modelServices.services().stream()
                .collect(Collectors.groupingBy(modelService -> modelService.service().type()));

        return (serviceType, methodFilter) -> {
            OpenApiBuilder builder = OpenApiBuilder.builder(serviceType, modelServices.deprecations(), metadata, methodFilter, extensionFilter);
            servicesByType.getOrDefault(serviceType, ImmutableList.of()).forEach(builder::addService);
            return builder.build();
        };
    }
}

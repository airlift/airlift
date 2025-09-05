package io.airlift.api.builders;

import io.airlift.api.ApiService;
import io.airlift.api.ApiServiceType;
import io.airlift.api.model.ModelServiceMetadata;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.validation.ValidatorException;

import java.net.URI;
import java.util.Arrays;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class ServiceMetadataBuilder
{
    private ServiceMetadataBuilder() {}

    public static ModelServiceMetadata buildServiceMetadata(Class<?> clazz)
    {
        ApiService apiService = clazz.getAnnotation(ApiService.class);
        if (apiService == null) {
            throw new ValidatorException("%s is not annotated with @ApiService".formatted(clazz));
        }

        try {
            return map((ApiService) apiService.type().getConstructor().newInstance());
        }
        catch (Exception e) {
            throw new ValidatorException("%s does not have a public no-arg constructor".formatted(apiService.type()));
        }
    }

    private static ModelServiceMetadata map(ApiService apiService)
    {
        ApiServiceType apiServiceType;
        try {
            apiServiceType = apiService.type().getConstructor().newInstance();
        }
        catch (Exception e) {
            throw new RuntimeException("Could not create ApiServiceType instance. The class must have a public no-arg constructor. " + apiService.type(), e);
        }

        return new ModelServiceMetadata(
                apiService.name(),
                map(apiServiceType),
                apiService.description(),
                Arrays.stream(apiService.documentationLinks())
                        .map(URI::create)
                        .collect(toImmutableList()));
    }

    private static ModelServiceType map(ApiServiceType apiServiceType)
    {
        return new ModelServiceType(apiServiceType.id(), apiServiceType.version(), apiServiceType.title(), apiServiceType.description(), apiServiceType.traits());
    }
}

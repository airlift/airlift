package io.airlift.api.openapi;

import io.airlift.api.model.ModelServiceType;
import io.airlift.api.openapi.models.OpenAPI;

import java.lang.reflect.Method;
import java.util.function.Predicate;

public interface OpenApiProvider
{
    OpenAPI build(ModelServiceType serviceType, Predicate<Method> methodFilter);
}

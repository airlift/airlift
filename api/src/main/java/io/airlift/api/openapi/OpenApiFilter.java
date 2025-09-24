package io.airlift.api.openapi;

import jakarta.ws.rs.container.ContainerRequestContext;

import java.lang.reflect.Method;
import java.util.function.Predicate;

@FunctionalInterface
public interface OpenApiFilter
{
    Predicate<Method> filterForRequest(ContainerRequestContext containerRequestContext);
}

package io.airlift.api.servertests.integration.testingserver;

import io.airlift.api.openapi.OpenApiFilter;
import jakarta.ws.rs.container.ContainerRequestContext;

import java.lang.reflect.Method;
import java.util.function.Predicate;

public class TestingOpenApiFilter
        implements OpenApiFilter, Predicate<Method>
{
    @Override
    public Predicate<Method> filterForRequest(ContainerRequestContext containerRequestContext)
    {
        if (containerRequestContext.getUriInfo().getRequestUri().getHost().equals("0.0.0.0")) {
            return ignore -> true;
        }
        return this;
    }

    @Override
    public boolean test(Method method)
    {
        return !method.getName().equals("hiddenMethod");
    }
}

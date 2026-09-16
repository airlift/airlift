package io.airlift.api.internals;

import io.airlift.api.ApiContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.stream.Stream;

public final class ParameterAnnotations
{
    private ParameterAnnotations() {}

    public static boolean isContextOrSuspendedParameter(Parameter parameter)
    {
        return parameter.isAnnotationPresent(Context.class) ||
                parameter.isAnnotationPresent(Suspended.class) ||
                hasApiContextAnnotation(parameter.getAnnotations());
    }

    public static boolean hasApiContextAnnotation(Annotation[] annotations)
    {
        return Stream.of(annotations)
                .anyMatch(annotation -> annotation.annotationType().isAnnotationPresent(ApiContext.class));
    }
}

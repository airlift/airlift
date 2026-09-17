package io.airlift.api;

import com.google.common.collect.ImmutableSet;
import io.airlift.api.internals.JacksonEnumValueResolver;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public final class ApiBuilderConfig
{
    public static final Set<Class<? extends Annotation>> DEFAULT_CONTEXT_ANNOTATIONS = ImmutableSet.of(Context.class, Suspended.class);

    private final ApiEnumValueResolver enumValueResolver;
    private final Set<Class<? extends Annotation>> contextAnnotations;

    private ApiBuilderConfig(ApiEnumValueResolver enumValueResolver, Set<Class<? extends Annotation>> contextAnnotations)
    {
        this.enumValueResolver = requireNonNull(enumValueResolver, "enumValueResolver is null");
        this.contextAnnotations = ImmutableSet.copyOf(contextAnnotations);
    }

    public static ApiBuilderConfig jackson()
    {
        return new ApiBuilderConfig(new JacksonEnumValueResolver(), DEFAULT_CONTEXT_ANNOTATIONS);
    }

    public static ApiBuilderConfig of(ApiEnumValueResolver enumValueResolver)
    {
        return new ApiBuilderConfig(enumValueResolver, DEFAULT_CONTEXT_ANNOTATIONS);
    }

    public ApiBuilderConfig withAdditionalContextAnnotation(Class<? extends Annotation> contextAnnotation)
    {
        requireNonNull(contextAnnotation, "contextAnnotation is null");
        validateContextAnnotation(contextAnnotation);

        ImmutableSet<Class<? extends Annotation>> updatedContextAnnotations = ImmutableSet.<Class<? extends Annotation>>builder()
                .addAll(contextAnnotations)
                .add(contextAnnotation)
                .build();
        return new ApiBuilderConfig(enumValueResolver, updatedContextAnnotations);
    }

    public ApiEnumValueResolver enumValueResolver()
    {
        return enumValueResolver;
    }

    public Set<Class<? extends Annotation>> contextAnnotations()
    {
        return contextAnnotations;
    }

    private static void validateContextAnnotation(Class<? extends Annotation> contextAnnotation)
    {
        // without RUNTIME retention the annotation is invisible to reflection, so the parameter would
        // silently be treated as un-annotated - consumed as the request body or dropped
        Retention retention = contextAnnotation.getAnnotation(Retention.class);
        checkArgument(
                (retention != null) && (retention.value() == RetentionPolicy.RUNTIME),
                "Context annotation must have RUNTIME retention: %s",
                contextAnnotation.getName());

        Target target = contextAnnotation.getAnnotation(Target.class);
        checkArgument(
                (target == null) || Arrays.asList(target.value()).contains(ElementType.PARAMETER),
                "Context annotation must be applicable to parameters: %s",
                contextAnnotation.getName());

        // registering a JAX-RS annotation would disable the check that keeps raw JAX-RS parameter
        // binding out of API methods (see MethodValidator.hasJaxRsAnnotation)
        checkArgument(
                !contextAnnotation.getPackageName().startsWith("jakarta.ws.rs"),
                "JAX-RS annotations cannot be registered as context annotations: %s",
                contextAnnotation.getName());
    }
}

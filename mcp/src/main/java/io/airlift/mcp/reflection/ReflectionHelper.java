package io.airlift.mcp.reflection;

import io.airlift.mcp.McpDescription;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.Content;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.reflection.MethodParameter.CallToolRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.GetPromptRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.HttpRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.IdentityParameter;
import io.airlift.mcp.reflection.MethodParameter.ObjectParameter;
import io.airlift.mcp.reflection.MethodParameter.ReadResourceRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.SourceResourceParameter;
import jakarta.servlet.http.HttpServletRequest;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.reflection.ReflectionHelper.parseParameters;

public interface ReflectionHelper
{
    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    static void validate(Method method, List<MethodParameter> methodParameters, Predicate<MethodParameter> parameterPredicateChain, Predicate<Method> methodPredicateChain)
    {
        Function<MethodParameter, String> methodDebug = parameter -> switch (parameter) {
            case ObjectParameter objectParameter -> objectParameter.name();
            default -> parameter.getClass().getSimpleName();
        };

        methodParameters.forEach(methodParameter -> {
            if (!parameterPredicateChain.test(methodParameter)) {
                throw new IllegalArgumentException("Parameter is invalid for method %s: %s.".formatted(method, methodDebug.apply(methodParameter)));
            }
        });

        if (!methodPredicateChain.test(method)) {
            throw new IllegalArgumentException("Return type is invalid for method: %s".formatted(method));
        }
    }

    static List<MethodParameter> parseParameters(Method method, Optional<? extends Class<?>> identityClass)
    {
        return IntStream.range(0, method.getParameterCount())
                .mapToObj(index -> {
                    Parameter parameter = method.getParameters()[index];
                    Type genericType = method.getGenericParameterTypes()[index];

                    if (parameter.getType().equals(HttpServletRequest.class)) {
                        return HttpRequestParameter.INSTANCE;
                    }

                    if (GetPromptRequest.class.isAssignableFrom(parameter.getType())) {
                        return GetPromptRequestParameter.INSTANCE;
                    }

                    if (CallToolRequest.class.isAssignableFrom(parameter.getType())) {
                        return CallToolRequestParameter.INSTANCE;
                    }

                    if (Resource.class.isAssignableFrom(parameter.getType())) {
                        return SourceResourceParameter.INSTANCE;
                    }

                    if (ReadResourceRequest.class.isAssignableFrom(parameter.getType())) {
                        return ReadResourceRequestParameter.INSTANCE;
                    }

                    if (identityClass.map(clazz -> clazz.isAssignableFrom(parameter.getType())).orElse(false)) {
                        return IdentityParameter.INSTANCE;
                    }

                    Optional<String> description = Optional.ofNullable(parameter.getAnnotation(McpDescription.class)).map(McpDescription::value);
                    return new ObjectParameter(parameter.getName(), parameter.getType(), genericType, description, Optional.class.isAssignableFrom(parameter.getType()));
                })
                .collect(toImmutableList());
    }

    interface InClassConsumer<A extends Annotation>
    {
        void accept(A annotation, Method method, List<MethodParameter> parameters);
    }

    static <A extends Annotation> void forAllInClass(Class<?> clazz, Class<A> annotationClass, Optional<? extends Class<?>> identityClass, InClassConsumer<A> consumer)
    {
        Stream.of(clazz.getMethods())
                .forEach(method -> {
                    A annotation = method.getAnnotation(annotationClass);
                    if (annotation == null) {
                        return;
                    }

                    List<MethodParameter> parameters = parseParameters(method, identityClass);
                    consumer.accept(annotation, method, parameters);
                });
    }

    static Content mapToContent(Object result)
    {
        return switch (result) {
            case String str -> new TextContent(str);
            case Number number -> new TextContent(number.toString());
            case Content content -> content;
            default -> new TextContent(String.valueOf(result));
        };
    }

    static Optional<Type> listArgument(Type type)
    {
        if (type instanceof ParameterizedType parameterizedType) {
            if (parameterizedType.getRawType().equals(List.class)) {
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments.length == 1) {
                    return Optional.of(actualTypeArguments[0]);
                }
            }
        }

        return Optional.empty();
    }

    static Optional<Type> optionalArgument(Type type)
    {
        if (type instanceof ParameterizedType parameterizedType) {
            if (parameterizedType.getRawType().equals(Optional.class)) {
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments.length == 1) {
                    return Optional.of(actualTypeArguments[0]);
                }
            }
        }

        return Optional.empty();
    }
}

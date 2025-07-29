package io.airlift.mcp.reflection;

import io.airlift.mcp.McpDescription;
import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.handler.ResourceTemplateHandler.PathTemplateValues;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CompletionRequest;
import io.airlift.mcp.model.Content;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.reflection.MethodParameter.CallToolRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.CompletionRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.GetPromptRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.HttpRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.NotifierParameter;
import io.airlift.mcp.reflection.MethodParameter.ObjectParameter;
import io.airlift.mcp.reflection.MethodParameter.PathTemplateValuesParameter;
import io.airlift.mcp.reflection.MethodParameter.ReadResourceRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.SessionIdParameter;
import io.airlift.mcp.reflection.MethodParameter.SourceResourceParameter;
import io.airlift.mcp.reflection.MethodParameter.SourceResourceTemplateParameter;
import io.airlift.mcp.session.SessionId;
import jakarta.ws.rs.core.Request;

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
                throw new IllegalArgumentException("Validation failure for %s. Parameter failed validation: %s.".formatted(method, methodDebug.apply(methodParameter)));
            }
        });

        if (!methodPredicateChain.test(method)) {
            throw new IllegalArgumentException("Method validation failure. Check that the return type is correct for: %s".formatted(method));
        }
    }

    static List<MethodParameter> parseParameters(Method method)
    {
        return IntStream.range(0, method.getParameterCount())
                .mapToObj(index -> {
                    Parameter parameter = method.getParameters()[index];
                    Type genericType = method.getGenericParameterTypes()[index];

                    if (Request.class.isAssignableFrom(parameter.getType())) {
                        return HttpRequestParameter.INSTANCE;
                    }

                    if (SessionId.class.isAssignableFrom(parameter.getType())) {
                        return SessionIdParameter.INSTANCE;
                    }

                    if (McpNotifier.class.isAssignableFrom(parameter.getType())) {
                        return NotifierParameter.INSTANCE;
                    }

                    if (CompletionRequest.class.isAssignableFrom(parameter.getType())) {
                        return CompletionRequestParameter.INSTANCE;
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

                    if (ResourceTemplate.class.isAssignableFrom(parameter.getType())) {
                        return SourceResourceTemplateParameter.INSTANCE;
                    }

                    if (ReadResourceRequest.class.isAssignableFrom(parameter.getType())) {
                        return ReadResourceRequestParameter.INSTANCE;
                    }

                    if (PathTemplateValues.class.isAssignableFrom(parameter.getType())) {
                        return PathTemplateValuesParameter.INSTANCE;
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

    static <A extends Annotation> void forAllInClass(Class<?> clazz, Class<A> annotationClass, InClassConsumer<A> consumer)
    {
        Stream.of(clazz.getMethods())
                .forEach(method -> {
                    A annotation = method.getAnnotation(annotationClass);
                    if (annotation == null) {
                        return;
                    }

                    List<MethodParameter> parameters = parseParameters(method);
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

    static Type listReturnArgument(Method method)
    {
        return listArgument(method.getGenericReturnType())
                .orElseThrow(() -> new IllegalArgumentException(String.format("Method %s does not return a List", method.getName())));
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

    static Type optionalReturnArgument(Method method)
    {
        return optionalArgument(method.getGenericReturnType())
                .orElseThrow(() -> new IllegalArgumentException(String.format("Method %s does not return an Optional", method.getName())));
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

    static void validateOnlyContexts(Method method, List<MethodParameter> parameters, Class<?> returnType)
    {
        if (!parameters.stream().allMatch(parameter -> (parameter instanceof HttpRequestParameter) || (parameter instanceof NotifierParameter))) {
            throw new IllegalArgumentException("Method " + method.getName() + " must only have HttpRequest or Notifier parameters");
        }

        Type genericReturnType = method.getGenericReturnType();
        if ((genericReturnType instanceof ParameterizedType parameterizedType) && parameterizedType.getRawType().equals(List.class)) {
            if (parameterizedType.getActualTypeArguments()[0].equals(returnType)) {
                return;
            }
        }

        throw new IllegalArgumentException(String.format("Method %s does not return %s", method.getName(), returnType.getSimpleName()));
    }
}

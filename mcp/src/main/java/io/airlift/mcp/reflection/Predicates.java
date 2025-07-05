package io.airlift.mcp.reflection;

import io.airlift.mcp.handler.ResourceTemplatesEntry;
import io.airlift.mcp.handler.ResourcesEntry;
import io.airlift.mcp.model.Completion;
import io.airlift.mcp.model.GetPromptResult;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.WildcardType;
import java.util.function.Predicate;

import static io.airlift.mcp.reflection.ReflectionHelper.listArgument;
import static io.airlift.mcp.reflection.ReflectionHelper.optionalReturnArgument;

public interface Predicates
{
    Predicate<MethodParameter> isHttpRequest = methodParameter -> methodParameter instanceof MethodParameter.HttpRequestParameter;
    Predicate<MethodParameter> isNotifier = methodParameter -> methodParameter instanceof MethodParameter.NotifierParameter;
    Predicate<MethodParameter> isCompletionRequest = methodParameter -> methodParameter instanceof MethodParameter.CompletionRequestParameter;
    Predicate<MethodParameter> isGetPromptRequest = methodParameter -> methodParameter instanceof MethodParameter.GetPromptRequestParameter;
    Predicate<MethodParameter> isCallToolRequest = methodParameter -> methodParameter instanceof MethodParameter.CallToolRequestParameter;
    Predicate<MethodParameter> isObject = methodParameter -> (methodParameter instanceof MethodParameter.ObjectParameter);
    Predicate<MethodParameter> isString = methodParameter -> (methodParameter instanceof MethodParameter.ObjectParameter objectParameter)
            && objectParameter.rawType().equals(String.class);

    Predicate<Method> returnsAnything = _ -> true;
    Predicate<Method> returnsOptionalCompletion = method -> optionalReturnArgument(method).equals(Completion.class);
    Predicate<Method> returnsResourceList = method -> method.getReturnType().equals(ResourcesEntry.class);
    Predicate<Method> returnsResourceTemplateList = method -> method.getReturnType().equals(ResourceTemplatesEntry.class);
    Predicate<Method> returnsString = method -> method.getReturnType().equals(String.class);
    Predicate<Method> returnsGetPromptResult = method -> method.getReturnType().equals(GetPromptResult.class)
            && (method.getGenericReturnType() instanceof ParameterizedType parameterizedType)
            && (parameterizedType.getActualTypeArguments()[0] instanceof WildcardType);
    Predicate<Method> returnsOptionalListOfString = method -> listArgument(optionalReturnArgument(method))
            .map(t -> t.equals(String.class))
            .orElse(false);
}

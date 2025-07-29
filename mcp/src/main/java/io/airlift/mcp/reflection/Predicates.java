package io.airlift.mcp.reflection;

import io.airlift.mcp.handler.ResourceTemplatesEntry;
import io.airlift.mcp.handler.ResourcesEntry;
import io.airlift.mcp.model.Completion;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.reflection.MethodParameter.CallToolRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.CompletionRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.GetPromptRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.HttpRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.NotifierParameter;
import io.airlift.mcp.reflection.MethodParameter.ObjectParameter;
import io.airlift.mcp.reflection.MethodParameter.SessionIdParameter;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.WildcardType;
import java.util.function.Predicate;

import static io.airlift.mcp.reflection.ReflectionHelper.listArgument;
import static io.airlift.mcp.reflection.ReflectionHelper.optionalReturnArgument;

public interface Predicates
{
    Predicate<MethodParameter> isHttpRequestOrSessonId = methodParameter -> (methodParameter instanceof HttpRequestParameter) || (methodParameter instanceof SessionIdParameter);
    Predicate<MethodParameter> isNotifier = methodParameter -> methodParameter instanceof NotifierParameter;
    Predicate<MethodParameter> isCompletionRequest = methodParameter -> methodParameter instanceof CompletionRequestParameter;
    Predicate<MethodParameter> isGetPromptRequest = methodParameter -> methodParameter instanceof GetPromptRequestParameter;
    Predicate<MethodParameter> isCallToolRequest = methodParameter -> methodParameter instanceof CallToolRequestParameter;
    Predicate<MethodParameter> isObject = methodParameter -> (methodParameter instanceof ObjectParameter);
    Predicate<MethodParameter> isString = methodParameter -> (methodParameter instanceof ObjectParameter objectParameter)
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

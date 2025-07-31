package io.airlift.mcp.reflection;

import io.airlift.mcp.model.Completion;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.reflection.MethodParameter.HttpRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.JaxrsContextParameter;
import io.airlift.mcp.reflection.MethodParameter.SessionIdParameter;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.WildcardType;
import java.util.function.Predicate;

import static io.airlift.mcp.reflection.ReflectionHelper.listArgument;
import static io.airlift.mcp.reflection.ReflectionHelper.optionalReturnArgument;

public interface Predicates
{
    Predicate<MethodParameter> isNotifier = methodParameter -> methodParameter instanceof MethodParameter.NotifierParameter;
    Predicate<MethodParameter> isCompletionRequest = methodParameter -> methodParameter instanceof MethodParameter.CompletionRequestParameter;
    Predicate<MethodParameter> isGetPromptRequest = methodParameter -> methodParameter instanceof MethodParameter.GetPromptRequestParameter;
    Predicate<MethodParameter> isCallToolRequest = methodParameter -> methodParameter instanceof MethodParameter.CallToolRequestParameter;
    Predicate<MethodParameter> isReadResourceRequest = methodParameter -> methodParameter instanceof MethodParameter.ReadResourceRequestParameter;
    Predicate<MethodParameter> isSourceResource = methodParameter -> methodParameter instanceof MethodParameter.SourceResourceParameter;
    Predicate<MethodParameter> isSourceResourceTemplate = methodParameter -> methodParameter instanceof MethodParameter.SourceResourceTemplateParameter;
    Predicate<MethodParameter> isPathTemplateValues = methodParameter -> methodParameter instanceof MethodParameter.PathTemplateValuesParameter;
    Predicate<MethodParameter> isObject = methodParameter -> (methodParameter instanceof MethodParameter.ObjectParameter);
    Predicate<MethodParameter> isString = methodParameter -> (methodParameter instanceof MethodParameter.ObjectParameter objectParameter)
            && objectParameter.rawType().equals(String.class);
    Predicate<MethodParameter> isRequestParameter = methodParameter -> (methodParameter instanceof HttpRequestParameter)
            || (methodParameter instanceof SessionIdParameter)
            || (methodParameter instanceof JaxrsContextParameter);

    Predicate<Method> returnsAnything = _ -> true;
    Predicate<Method> returnsOptionalCompletion = method -> optionalReturnArgument(method).equals(Completion.class);
    Predicate<Method> returnsResourceContents = method -> method.getReturnType().equals(ResourceContents.class);
    Predicate<Method> returnsResourceContentsList = method -> listArgument(method.getGenericReturnType())
            .map(t -> t.equals(ResourceContents.class))
            .orElse(false);
    Predicate<Method> returnsString = method -> method.getReturnType().equals(String.class);
    Predicate<Method> returnsGetPromptResult = method -> method.getReturnType().equals(GetPromptResult.class)
            && (method.getGenericReturnType() instanceof ParameterizedType parameterizedType)
            && (parameterizedType.getActualTypeArguments()[0] instanceof WildcardType);
    Predicate<Method> returnsOptionalListOfString = method -> listArgument(optionalReturnArgument(method))
            .map(t -> t.equals(String.class))
            .orElse(false);
}

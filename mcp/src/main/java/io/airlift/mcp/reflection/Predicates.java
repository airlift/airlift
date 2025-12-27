package io.airlift.mcp.reflection;

import io.airlift.mcp.model.CompleteResult.CompleteCompletion;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.reflection.MethodParameter.CallToolRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.CompleteArgumentParameter;
import io.airlift.mcp.reflection.MethodParameter.CompleteContextParameter;
import io.airlift.mcp.reflection.MethodParameter.GetPromptRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.HttpRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.IdentityParameter;
import io.airlift.mcp.reflection.MethodParameter.McpRequestContextParameter;
import io.airlift.mcp.reflection.MethodParameter.ObjectParameter;
import io.airlift.mcp.reflection.MethodParameter.ReadResourceRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.ResourceTemplateValuesParameter;
import io.airlift.mcp.reflection.MethodParameter.SourceResourceParameter;
import io.airlift.mcp.reflection.MethodParameter.SourceResourceTemplateParameter;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.WildcardType;
import java.util.function.Predicate;

import static io.airlift.mcp.reflection.ReflectionHelper.listArgument;

public interface Predicates
{
    Predicate<MethodParameter> isHttpRequestOrContext = methodParameter -> (methodParameter instanceof HttpRequestParameter) || (methodParameter instanceof McpRequestContextParameter);
    Predicate<MethodParameter> isIdentity = methodParameter -> methodParameter instanceof IdentityParameter;
    Predicate<MethodParameter> isGetPromptRequest = methodParameter -> methodParameter instanceof GetPromptRequestParameter;
    Predicate<MethodParameter> isCallToolRequest = methodParameter -> methodParameter instanceof CallToolRequestParameter;
    Predicate<MethodParameter> isReadResourceRequest = methodParameter -> methodParameter instanceof ReadResourceRequestParameter;
    Predicate<MethodParameter> isCompleteArgument = methodParameter -> methodParameter instanceof CompleteArgumentParameter;
    Predicate<MethodParameter> isCompleteContext = methodParameter -> methodParameter instanceof CompleteContextParameter;
    Predicate<MethodParameter> isSourceResource = methodParameter -> methodParameter instanceof SourceResourceParameter;
    Predicate<MethodParameter> isSourceResourceTemplate = methodParameter -> methodParameter instanceof SourceResourceTemplateParameter;
    Predicate<MethodParameter> isResourceTemplateValues = methodParameter -> methodParameter instanceof ResourceTemplateValuesParameter;
    Predicate<MethodParameter> isObject = methodParameter -> (methodParameter instanceof ObjectParameter);
    Predicate<MethodParameter> isString = methodParameter -> (methodParameter instanceof ObjectParameter objectParameter)
            && objectParameter.rawType().equals(String.class);

    Predicate<Method> returnsAnything = _ -> true;
    Predicate<Method> returnsResourceContents = method -> method.getReturnType().equals(ResourceContents.class);
    Predicate<Method> returnsCompleteCompletion = method -> method.getReturnType().equals(CompleteCompletion.class);
    Predicate<Method> returnsResourceContentsList = method -> listArgument(method.getGenericReturnType())
            .map(t -> t.equals(ResourceContents.class))
            .orElse(false);
    Predicate<Method> returnsStringList = method -> listArgument(method.getGenericReturnType())
            .map(t -> t.equals(String.class))
            .orElse(false);
    Predicate<Method> returnsString = method -> method.getReturnType().equals(String.class);
    Predicate<Method> returnsGetPromptResult = method -> method.getReturnType().equals(GetPromptResult.class)
            && (method.getGenericReturnType() instanceof ParameterizedType parameterizedType)
            && (parameterizedType.getActualTypeArguments()[0] instanceof WildcardType);
}

package io.airlift.mcp.reflection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import io.airlift.jsonrpc.model.JsonRpcErrorCode;
import io.airlift.mcp.handler.ListResourceTemplatesHandler;
import io.airlift.mcp.handler.ResourceTemplatesEntry;

import java.lang.reflect.Method;
import java.util.List;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.reflection.Predicates.isHttpRequestOrSessonId;
import static io.airlift.mcp.reflection.Predicates.isNotifier;
import static io.airlift.mcp.reflection.Predicates.returnsResourceTemplateList;
import static io.airlift.mcp.reflection.ReflectionHelper.validate;
import static java.util.Objects.requireNonNull;

public class ListResourceTemplatesHandlerProvider
        implements Provider<ListResourceTemplatesHandler>
{
    private final Class<?> clazz;
    private final Method method;
    private final List<MethodParameter> parameters;
    @Inject private Injector injector;
    @Inject private ObjectMapper objectMapper;

    public ListResourceTemplatesHandlerProvider(Class<?> clazz, Method method, List<MethodParameter> parameters)
    {
        this.clazz = requireNonNull(clazz, "clazz is null");
        this.method = requireNonNull(method, "method is null");
        this.parameters = ImmutableList.copyOf(parameters);

        validate(method, parameters, isHttpRequestOrSessonId.or(isNotifier), returnsResourceTemplateList);
    }

    @Override
    public ListResourceTemplatesHandler get()
    {
        Object instance = injector.getInstance(clazz);
        MethodInvoker methodInvoker = new MethodInvoker(instance, method, parameters, objectMapper);
        return (request, sessionId, notifier) -> {
            Object result = methodInvoker.builder(request, sessionId)
                    .withNotifier(notifier)
                    .invoke();
            if (result == null) {
                throw exception(JsonRpcErrorCode.INTERNAL_ERROR, "ListResourceTemplates %s returned null".formatted(method.getName()));
            }
            return (ResourceTemplatesEntry) result;
        };
    }
}

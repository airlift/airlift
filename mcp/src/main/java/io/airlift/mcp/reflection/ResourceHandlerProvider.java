package io.airlift.mcp.reflection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import io.airlift.jsonrpc.model.JsonRpcErrorCode;
import io.airlift.mcp.McpResource;
import io.airlift.mcp.handler.ResourceEntry;
import io.airlift.mcp.handler.ResourceHandler;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.Role;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.reflection.Predicates.isHttpRequestOrSessonId;
import static io.airlift.mcp.reflection.Predicates.isNotifier;
import static io.airlift.mcp.reflection.Predicates.isReadResourceRequest;
import static io.airlift.mcp.reflection.Predicates.isSourceResource;
import static io.airlift.mcp.reflection.Predicates.returnsResourceContents;
import static io.airlift.mcp.reflection.Predicates.returnsResourceContentsList;
import static io.airlift.mcp.reflection.ReflectionHelper.validate;
import static java.lang.Double.isNaN;
import static java.util.Objects.requireNonNull;

public class ResourceHandlerProvider
        implements Provider<ResourceEntry>
{
    private final Resource resource;
    private final Class<?> clazz;
    private final Method method;
    private final List<MethodParameter> parameters;
    private final boolean resultIsSingleContent;
    @Inject private Injector injector;
    @Inject private ObjectMapper objectMapper;

    public ResourceHandlerProvider(McpResource mcpResource, Class<?> clazz, Method method, List<MethodParameter> parameters)
    {
        this.clazz = requireNonNull(clazz, "clazz is null");
        this.method = requireNonNull(method, "method is null");
        this.parameters = ImmutableList.copyOf(parameters);

        validate(method, parameters, isHttpRequestOrSessonId.or(isNotifier).or(isReadResourceRequest).or(isSourceResource), returnsResourceContents.or(returnsResourceContentsList));
        resultIsSingleContent = returnsResourceContents.test(method);

        resource = buildResource(
                mcpResource.name(),
                mcpResource.uri(),
                mcpResource.mimeType(),
                mcpResource.description(),
                mcpResource.size(),
                mcpResource.audience(),
                mcpResource.priority());
    }

    @Override
    public ResourceEntry get()
    {
        Object instance = injector.getInstance(clazz);
        MethodInvoker methodInvoker = new MethodInvoker(instance, method, parameters, objectMapper);

        ResourceHandler resourceHandler = (request, sessionId, notifier, sourceResource, readResourceRequest) -> {
            Object result = methodInvoker.builder(request, sessionId)
                    .withNotifier(notifier)
                    .withReadResourceRequest(sourceResource, readResourceRequest)
                    .invoke();
            return mapResult(method, result, resultIsSingleContent);
        };

        return new ResourceEntry(resource, resourceHandler);
    }

    @SuppressWarnings("unchecked")
    static List<ResourceContents> mapResult(Method method, Object result, boolean resultIsSingleContent)
    {
        if (result == null) {
            throw exception(JsonRpcErrorCode.INTERNAL_ERROR, "ResourceHandler %s returned null".formatted(method.getName()));
        }

        if (resultIsSingleContent) {
            return ImmutableList.of((ResourceContents) result);
        }

        return (List<ResourceContents>) result;
    }

    static Resource buildResource(String name, String uri, String mimeType, String descriptionOrEmpty, long size, Role[] audience, double priority)
    {
        Optional<String> description = descriptionOrEmpty.isEmpty() ? Optional.empty() : Optional.of(descriptionOrEmpty);

        Resource.Annotations annotations = new Resource.Annotations(
                ImmutableList.copyOf(audience),
                isNaN(priority) ? Optional.empty() : Optional.of(priority));

        Optional<Long> useSize = (size >= 0) ? Optional.of(size) : Optional.empty();
        Optional<Resource.Annotations> useAnnotations = annotations.equals(Resource.Annotations.EMPTY) ? Optional.empty() : Optional.of(annotations);
        return new Resource(name, uri, description, mimeType, useSize, useAnnotations);
    }
}

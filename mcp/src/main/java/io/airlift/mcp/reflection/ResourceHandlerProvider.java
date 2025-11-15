package io.airlift.mcp.reflection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import io.airlift.mcp.McpResource;
import io.airlift.mcp.handler.ResourceEntry;
import io.airlift.mcp.handler.ResourceHandler;
import io.airlift.mcp.model.Annotations;
import io.airlift.mcp.model.JsonRpcErrorCode;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.Role;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.reflection.Predicates.isHttpRequestOrContext;
import static io.airlift.mcp.reflection.Predicates.isIdentity;
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
    private Injector injector;
    private ObjectMapper objectMapper;

    public ResourceHandlerProvider(McpResource mcpResource, Class<?> clazz, Method method, List<MethodParameter> parameters)
    {
        this.clazz = requireNonNull(clazz, "clazz is null");
        this.method = requireNonNull(method, "method is null");
        this.parameters = ImmutableList.copyOf(parameters);

        validate(method, parameters, isHttpRequestOrContext.or(isIdentity).or(isReadResourceRequest).or(isSourceResource), returnsResourceContents.or(returnsResourceContentsList));
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

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = injector;
    }

    @Inject
    public void setObjectMapper(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    @Override
    public ResourceEntry get()
    {
        Provider<?> instance = injector.getProvider(clazz);
        MethodInvoker methodInvoker = new MethodInvoker(instance, method, parameters, objectMapper);

        ResourceHandler resourceHandler = (requestContext, sourceResource, readResourceRequest) -> {
            Object result = methodInvoker.builder(requestContext)
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

        Annotations annotations = new Annotations(
                ImmutableList.copyOf(audience),
                isNaN(priority) ? OptionalDouble.empty() : OptionalDouble.of(priority));

        OptionalLong useSize = (size >= 0) ? OptionalLong.of(size) : OptionalLong.empty();
        Optional<Annotations> useAnnotations = annotations.equals(Annotations.EMPTY) ? Optional.empty() : Optional.of(annotations);
        return new Resource(name, uri, description, mimeType, useSize, useAnnotations);
    }
}

package io.airlift.mcp.reflection;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import io.airlift.mcp.McpResource;
import io.airlift.mcp.handler.ResourceEntry;
import io.airlift.mcp.handler.ResourceHandler;
import io.airlift.mcp.model.Annotations;
import io.airlift.mcp.model.ReadResourceResponse;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.Role;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.reflection.Predicates.isHttpRequestOrContext;
import static io.airlift.mcp.reflection.Predicates.isIdentity;
import static io.airlift.mcp.reflection.Predicates.isReadResourceRequest;
import static io.airlift.mcp.reflection.Predicates.isSourceResource;
import static io.airlift.mcp.reflection.Predicates.returnsReadResourceResponse;
import static io.airlift.mcp.reflection.Predicates.returnsReadResourceResult;
import static io.airlift.mcp.reflection.Predicates.returnsResourceContents;
import static io.airlift.mcp.reflection.Predicates.returnsResourceContentsList;
import static io.airlift.mcp.reflection.ReflectionHelper.validate;
import static io.airlift.mcp.reflection.ResourceHandlerProvider.ResultType.CONTENT_LIST;
import static io.airlift.mcp.reflection.ResourceHandlerProvider.ResultType.READ_RESOURCE_RESPONSE;
import static io.airlift.mcp.reflection.ResourceHandlerProvider.ResultType.READ_RESOURCE_RESULT;
import static io.airlift.mcp.reflection.ResourceHandlerProvider.ResultType.SINGLE_CONTENT;
import static java.lang.Double.isNaN;
import static java.util.Objects.requireNonNull;

public class ResourceHandlerProvider
        implements Provider<ResourceEntry>
{
    private final Resource resource;
    private final Class<?> clazz;
    private final Method method;
    private final List<MethodParameter> parameters;
    private final List<String> icons;
    private final ResultType resultType;
    private Injector injector;
    private JsonMapper jsonMapper;

    public ResourceHandlerProvider(McpResource mcpResource, Class<?> clazz, Method method, List<MethodParameter> parameters)
    {
        this.clazz = requireNonNull(clazz, "clazz is null");
        this.method = requireNonNull(method, "method is null");
        this.parameters = ImmutableList.copyOf(parameters);
        icons = ImmutableList.copyOf(mcpResource.icons());

        validate(method, parameters, isHttpRequestOrContext.or(isIdentity).or(isReadResourceRequest).or(isSourceResource), returnsResourceContents.or(returnsResourceContentsList).or(returnsReadResourceResult).or(returnsReadResourceResponse));
        resultType = determineResultType(method);

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
    public void setJsonMapper(JsonMapper jsonMapper)
    {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public ResourceEntry get()
    {
        Provider<?> instance = injector.getProvider(clazz);
        MethodInvoker methodInvoker = new MethodInvoker(instance, method, parameters, jsonMapper);
        IconHelper iconHelper = injector.getInstance(IconHelper.class);

        ResourceHandler resourceHandler = (requestContext, sourceResource, readResourceRequest, allowIncompleteResult) -> {
            Object result = methodInvoker.builder(requestContext)
                    .withReadResourceRequest(sourceResource, readResourceRequest)
                    .withAllowIncompleteResult(allowIncompleteResult)
                    .invoke();
            return mapResult(method, result, resultType);
        };

        return new ResourceEntry(resource.withIcons(iconHelper.mapIcons(icons)), resourceHandler);
    }

    enum ResultType
    {
        SINGLE_CONTENT,
        CONTENT_LIST,
        READ_RESOURCE_RESULT,
        READ_RESOURCE_RESPONSE,
    }

    static ResultType determineResultType(Method method)
    {
        if (returnsResourceContents.test(method)) {
            return SINGLE_CONTENT;
        }
        if (returnsResourceContentsList.test(method)) {
            return CONTENT_LIST;
        }
        if (returnsReadResourceResult.test(method)) {
            return READ_RESOURCE_RESULT;
        }
        if (returnsReadResourceResponse.test(method)) {
            return READ_RESOURCE_RESPONSE;
        }
        throw new IllegalArgumentException("Method %s does not have a valid return type".formatted(method.getName()));
    }

    @SuppressWarnings("unchecked")
    static ReadResourceResponse mapResult(Method method, Object result, ResultType resultType)
    {
        if (result == null) {
            throw exception(INTERNAL_ERROR, "ResourceHandler %s returned null".formatted(method.getName()));
        }

        return switch (resultType) {
            case CONTENT_LIST -> new ReadResourceResult((List<ResourceContents>) result);
            case READ_RESOURCE_RESULT -> (ReadResourceResult) result;
            case READ_RESOURCE_RESPONSE -> (ReadResourceResponse) result;
            case SINGLE_CONTENT -> new ReadResourceResult(ImmutableList.of((ResourceContents) result));
        };
    }

    private static Resource buildResource(String name, String uri, String mimeType, String descriptionOrEmpty, long size, Role[] audience, double priority)
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

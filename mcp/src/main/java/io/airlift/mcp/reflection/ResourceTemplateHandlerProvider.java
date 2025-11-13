package io.airlift.mcp.reflection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import io.airlift.mcp.McpResourceTemplate;
import io.airlift.mcp.handler.ResourceTemplateEntry;
import io.airlift.mcp.handler.ResourceTemplateHandler;
import io.airlift.mcp.model.Annotations;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.Role;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static io.airlift.mcp.reflection.Predicates.isHttpRequestOrContext;
import static io.airlift.mcp.reflection.Predicates.isIdentity;
import static io.airlift.mcp.reflection.Predicates.isReadResourceRequest;
import static io.airlift.mcp.reflection.Predicates.isResourceTemplateValues;
import static io.airlift.mcp.reflection.Predicates.isSourceResourceTemplate;
import static io.airlift.mcp.reflection.Predicates.returnsResourceContents;
import static io.airlift.mcp.reflection.Predicates.returnsResourceContentsList;
import static io.airlift.mcp.reflection.ReflectionHelper.validate;
import static io.airlift.mcp.reflection.ResourceHandlerProvider.mapResult;
import static java.lang.Double.isNaN;
import static java.util.Objects.requireNonNull;

public class ResourceTemplateHandlerProvider
        implements Provider<ResourceTemplateEntry>
{
    private final ResourceTemplate resourceTemplate;
    private final Class<?> clazz;
    private final Method method;
    private final List<MethodParameter> parameters;
    private final boolean resultIsSingleContent;
    private Injector injector;
    private ObjectMapper objectMapper;

    public ResourceTemplateHandlerProvider(McpResourceTemplate mcpResourceTemplate, Class<?> clazz, Method method, List<MethodParameter> parameters)
    {
        this.clazz = requireNonNull(clazz, "clazz is null");
        this.method = requireNonNull(method, "method is null");
        this.parameters = ImmutableList.copyOf(parameters);

        validate(method, parameters, isHttpRequestOrContext.or(isIdentity).or(isReadResourceRequest).or(isSourceResourceTemplate).or(isResourceTemplateValues), returnsResourceContents.or(returnsResourceContentsList));
        this.resultIsSingleContent = returnsResourceContents.test(method);

        this.resourceTemplate = buildResourceTemplate(
                mcpResourceTemplate.name(),
                mcpResourceTemplate.uriTemplate(),
                mcpResourceTemplate.mimeType(),
                mcpResourceTemplate.description(),
                mcpResourceTemplate.audience(),
                mcpResourceTemplate.priority());
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
    public ResourceTemplateEntry get()
    {
        Provider<?> instance = injector.getProvider(clazz);
        MethodInvoker methodInvoker = new MethodInvoker(instance, method, parameters, objectMapper);

        ResourceTemplateHandler resourceTemplateHandler = (requestContext, sourceResourceTemplate, readResourceRequest, resourceTemplateValues) -> {
            Object result = methodInvoker.builder(requestContext)
                    .withReadResourceTemplateRequest(sourceResourceTemplate, readResourceRequest)
                    .withResourceTemplateValues(resourceTemplateValues)
                    .invoke();
            return mapResult(method, result, resultIsSingleContent);
        };

        return new ResourceTemplateEntry(resourceTemplate, resourceTemplateHandler);
    }

    static ResourceTemplate buildResourceTemplate(String name, String uriTemplate, String mimeType, String descriptionOrEmpty, Role[] audience, double priority)
    {
        Optional<String> description = descriptionOrEmpty.isEmpty() ? Optional.empty() : Optional.of(descriptionOrEmpty);

        Annotations annotations = new Annotations(
                ImmutableList.copyOf(audience),
                isNaN(priority) ? OptionalDouble.empty() : OptionalDouble.of(priority));

        Optional<Annotations> useAnnotations = annotations.equals(Annotations.EMPTY) ? Optional.empty() : Optional.of(annotations);
        return new ResourceTemplate(name, uriTemplate, description, mimeType, useAnnotations);
    }
}

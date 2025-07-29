package io.airlift.mcp.reflection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import io.airlift.mcp.McpResourceTemplate;
import io.airlift.mcp.handler.ResourceTemplateEntry;
import io.airlift.mcp.handler.ResourceTemplateHandler;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceTemplate;

import java.lang.reflect.Method;
import java.util.List;

import static io.airlift.mcp.reflection.Predicates.isHttpRequestOrSessonId;
import static io.airlift.mcp.reflection.Predicates.isNotifier;
import static io.airlift.mcp.reflection.Predicates.isPathTemplateValues;
import static io.airlift.mcp.reflection.Predicates.isReadResourceRequest;
import static io.airlift.mcp.reflection.Predicates.isSourceResourceTemplate;
import static io.airlift.mcp.reflection.Predicates.returnsResourceContents;
import static io.airlift.mcp.reflection.Predicates.returnsResourceContentsList;
import static io.airlift.mcp.reflection.ReflectionHelper.validate;
import static io.airlift.mcp.reflection.ResourceHandlerProvider.mapResult;
import static java.util.Objects.requireNonNull;

public class ResourceTemplateHandlerProvider
        implements Provider<ResourceTemplateEntry>
{
    private final ResourceTemplate resourceTemplate;
    private final Class<?> clazz;
    private final Method method;
    private final List<MethodParameter> parameters;
    private final boolean resultIsSingleContent;
    @Inject private Injector injector;
    @Inject private ObjectMapper objectMapper;

    public ResourceTemplateHandlerProvider(McpResourceTemplate mcpResourceTemplate, Class<?> clazz, Method method, List<MethodParameter> parameters)
    {
        this.clazz = requireNonNull(clazz, "clazz is null");
        this.method = requireNonNull(method, "method is null");
        this.parameters = ImmutableList.copyOf(parameters);

        validate(method, parameters, isHttpRequestOrSessonId.or(isNotifier).or(isReadResourceRequest).or(isSourceResourceTemplate).or(isPathTemplateValues), returnsResourceContents.or(returnsResourceContentsList));
        resultIsSingleContent = returnsResourceContents.test(method);

        resourceTemplate = buildResourceTemplate(mcpResourceTemplate);
    }

    @Override
    public ResourceTemplateEntry get()
    {
        Object instance = injector.getInstance(clazz);
        MethodInvoker methodInvoker = new MethodInvoker(instance, method, parameters, objectMapper);

        ResourceTemplateHandler resourceTemplateHandler = (request, sessionId, notifier, sourceResourceTemplate, readResourceRequest, pathTemplateValues) -> {
            Object result = methodInvoker.builder(request, sessionId)
                    .withNotifier(notifier)
                    .withReadResourceTemplateRequest(sourceResourceTemplate, readResourceRequest, pathTemplateValues)
                    .invoke();
            return mapResult(method, result, resultIsSingleContent);
        };

        return new ResourceTemplateEntry(resourceTemplate, resourceTemplateHandler);
    }

    private static ResourceTemplate buildResourceTemplate(McpResourceTemplate resourceTemplate)
    {
        Resource resource = ResourceHandlerProvider.buildResource(
                resourceTemplate.name(),
                resourceTemplate.uriTemplate(),
                resourceTemplate.mimeType(),
                resourceTemplate.description(),
                resourceTemplate.size(),
                resourceTemplate.audience(),
                resourceTemplate.priority());
        return new ResourceTemplate(resource.name(), resource.uri(), resource.description(), resource.mimeType(), resource.size(), resource.annotations());
    }
}

package io.airlift.mcp.reflection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import io.airlift.mcp.McpApp;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpTool;
import io.airlift.mcp.handler.ResourceEntry;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.handler.ToolHandler;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content;
import io.airlift.mcp.model.JsonSchemaBuilder;
import io.airlift.mcp.model.StructuredContent;
import io.airlift.mcp.model.StructuredContentResult;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.model.Tool.Execution;
import io.airlift.mcp.model.UiToolVisibility;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonSchemaBuilder.isPrimitiveType;
import static io.airlift.mcp.model.JsonSchemaBuilder.isSupportedType;
import static io.airlift.mcp.reflection.Predicates.isCallToolRequest;
import static io.airlift.mcp.reflection.Predicates.isHttpRequestOrContext;
import static io.airlift.mcp.reflection.Predicates.isIdentity;
import static io.airlift.mcp.reflection.Predicates.isObject;
import static io.airlift.mcp.reflection.Predicates.returnsAnything;
import static io.airlift.mcp.reflection.ReflectionHelper.mapToContent;
import static io.airlift.mcp.reflection.ReflectionHelper.requiredArgument;
import static io.airlift.mcp.reflection.ReflectionHelper.validate;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class ToolHandlerProvider
        implements Provider<ToolEntry>
{
    private final Tool tool;
    private final Class<?> clazz;
    private final Method method;
    private final List<MethodParameter> parameters;
    private final Map<String, AppContent> apps;
    private final ReturnType returnType;
    private final List<String> icons;
    private final Consumer<Provider<ResourceEntry>> resourceHandlerConsumer;
    private Injector injector;
    private ObjectMapper objectMapper;

    public ToolHandlerProvider(McpTool mcpTool, Class<?> clazz, Method method, List<MethodParameter> parameters, Map<String, AppContent> apps, Consumer<Provider<ResourceEntry>> resourceHandlerConsumer)
    {
        this.clazz = requireNonNull(clazz, "clazz is null");
        this.method = requireNonNull(method, "method is null");
        this.parameters = ImmutableList.copyOf(parameters);
        this.apps = requireNonNull(apps, "apps is null");   // do not copy
        icons = ImmutableList.copyOf(mcpTool.icons());
        this.resourceHandlerConsumer = requireNonNull(resourceHandlerConsumer, "resourceHandlerConsumer is null");

        validate(method, parameters, isHttpRequestOrContext.or(isIdentity).or(isObject).or(isCallToolRequest), returnsAnything);

        tool = buildTool(mcpTool, method, parameters);

        if (void.class.equals(method.getReturnType())) {
            returnType = ReturnType.VOID;
        }
        else if (CallToolResult.class.isAssignableFrom(method.getReturnType())) {
            returnType = ReturnType.CALL_TOOL_RESULT;
        }
        else if (StructuredContentResult.class.isAssignableFrom(method.getReturnType())) {
            returnType = ReturnType.STRUCTURED_RESULT;
        }
        else if (Content.class.isAssignableFrom(method.getReturnType()) || isPrimitiveType(method.getGenericReturnType())) {
            returnType = ReturnType.CONTENT;
        }
        else if (isSupportedType(method.getGenericReturnType())) {
            returnType = ReturnType.STRUCTURED;
        }
        else {
            throw exception(INVALID_PARAMS, "Method %s has unsupported return type: %s".formatted(method.getName(), method.getGenericReturnType()));
        }
    }

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = requireNonNull(injector, "injector is null");
    }

    @Inject
    public void setObjectMapper(ObjectMapper objectMapper)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
    }

    private enum ReturnType {
        VOID,
        CALL_TOOL_RESULT,
        CONTENT,
        STRUCTURED,
        STRUCTURED_RESULT,
    }

    @Override
    public ToolEntry get()
    {
        Provider<?> instance = injector.getProvider(clazz);
        MethodInvoker methodInvoker = new MethodInvoker(instance, method, parameters, objectMapper);
        IconHelper iconHelper = injector.getInstance(IconHelper.class);

        ToolHandler toolHandler = (requestContext, toolRequest) -> {
            Object result = methodInvoker.builder(requestContext)
                    .withArguments(toolRequest.arguments())
                    .withCallToolRequest(toolRequest)
                    .invoke();
            if (result == null && returnType != ReturnType.VOID) {
                throw exception(INTERNAL_ERROR, "Tool %s returned null".formatted(method.getName()));
            }

            return switch (returnType) {
                case VOID -> new CallToolResult(ImmutableList.of());
                case CONTENT -> new CallToolResult(mapToContent(result));
                case STRUCTURED -> new CallToolResult(Optional.of(ImmutableList.of(mapToContent(result))), Optional.of(new StructuredContent<>(result)), Optional.empty(), Optional.of(false), Optional.empty());
                case CALL_TOOL_RESULT -> (CallToolResult) result;
                case STRUCTURED_RESULT -> mapStructuredContentResult((StructuredContentResult<?>) result);
            };
        };

        return new ToolEntry(tool.withIcons(iconHelper.mapIcons(icons)), toolHandler);
    }

    private CallToolResult mapStructuredContentResult(StructuredContentResult<?> result)
    {
        return new CallToolResult(Optional.of(result.content()), result.structuredContent().map(StructuredContent::new), Optional.empty(), Optional.of(result.isError()), Optional.empty());
    }

    private Tool buildTool(McpTool tool, Method method, List<MethodParameter> parameters)
    {
        Optional<String> description = tool.description().isEmpty() ? Optional.empty() : Optional.of(tool.description());
        Optional<String> title = tool.title().isEmpty() ? Optional.empty() : Optional.of(tool.title());

        Tool.ToolAnnotations toolAnnotations = new Tool.ToolAnnotations(
                title,
                tool.readOnlyHint().toJsonValue(),
                tool.destructiveHint().toJsonValue(),
                tool.idempotentHint().toJsonValue(),
                tool.openWorldHint().toJsonValue());

        Optional<ObjectNode> outputSchema;
        if (CallToolResult.class.isAssignableFrom(method.getReturnType())) {
            outputSchema = Optional.empty();
        }
        else if (StructuredContentResult.class.isAssignableFrom(method.getReturnType())) {
            JsonSchemaBuilder jsonSchemaBuilder = new JsonSchemaBuilder("Tool (return): " + tool.name());
            outputSchema = Optional.of(jsonSchemaBuilder.build(description, requiredArgument(method.getGenericReturnType())));
        }
        else if (method.getReturnType().isRecord()) {
            JsonSchemaBuilder jsonSchemaBuilder = new JsonSchemaBuilder("Tool (return): " + tool.name());
            outputSchema = Optional.of(jsonSchemaBuilder.build(description, method.getReturnType()));
        }
        else {
            outputSchema = Optional.empty();
        }

        JsonSchemaBuilder jsonSchemaBuilder = new JsonSchemaBuilder("Tool: " + tool.name());
        ObjectNode jsonSchema = jsonSchemaBuilder.build(description, parameters);

        return applyApp(new Tool(tool.name(), description, title, jsonSchema, outputSchema, toolAnnotations, Optional.empty(), Optional.of(new Execution(tool.execution())), Optional.empty()), tool);
    }

    private Tool applyApp(Tool tool, McpTool mcpTool)
    {
        McpApp app = mcpTool.app();
        if (app.resourceUri().isEmpty()) {
            return tool;
        }

        if (app.resourceUri().isBlank()) {
            throw exception(INVALID_PARAMS, "app.resourceUri cannot be blank for Tool: %s".formatted(tool.name()));
        }
        if (!app.resourceUri().startsWith("ui://")) {
            throw exception(INVALID_PARAMS, "app.resourceUri must use scheme \"ui://\" for Tool: %s".formatted(tool.name()));
        }

        AppContent appContent = apps.get(app.resourceUri());
        if (appContent == null) {
            if (app.sourcePath().isBlank()) {
                throw exception(INVALID_PARAMS, "app.sourcePath cannot be blank for Tool: %s".formatted(tool.name()));
            }

            appContent = new AppContent(app.sourcePath(), loadContent(tool, app), () -> loadContent(tool, app));
            apps.put(app.resourceUri(), appContent);
        }
        else if (!app.sourcePath().isEmpty() && !app.sourcePath().equals(appContent.sourcePath())) {
            throw exception(INVALID_PARAMS, "%s was previously specified but its sourcePath does not match the sourcePath of the provided app content for Tool: %s".formatted(app.resourceUri(), tool.name()));
        }

        ImmutableMap.Builder<String, Object> ui = ImmutableMap.builder();
        ui.put("resourceUri", app.resourceUri());
        if (app.visibility().length > 0) {
            ui.put("visibility", Stream.of(app.visibility()).map(UiToolVisibility::toJsonValue).collect(toImmutableSet()));
        }

        Supplier<String> contentLoader = app.debugMode() ? appContent.contentLoader() : appContent::content;
        resourceHandlerConsumer.accept(new AppResourceHandlerProvider(app, tool.name(), tool.description(), contentLoader, appContent.content().length()));

        return tool.withMeta(ImmutableMap.of("ui", ui.build()));
    }

    private static String loadContent(Tool tool, McpApp app)
    {
        try {
            return Resources.toString(Resources.getResource(app.sourcePath()), UTF_8);
        }
        catch (Exception e) {
            McpException exception = exception(INVALID_PARAMS, "Could not load app.sourcePath for Tool: %s".formatted(tool.name()));
            exception.initCause(e);
            throw exception;
        }
    }
}

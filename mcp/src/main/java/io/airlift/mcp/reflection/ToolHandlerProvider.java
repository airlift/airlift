package io.airlift.mcp.reflection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import io.airlift.jsonrpc.model.JsonRpcErrorCode;
import io.airlift.mcp.McpTool;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.handler.ToolHandler;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content;
import io.airlift.mcp.model.JsonSchemaBuilder;
import io.airlift.mcp.model.StructuredContent;
import io.airlift.mcp.model.Tool;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonSchemaBuilder.isPrimitiveType;
import static io.airlift.mcp.model.JsonSchemaBuilder.isSupportedType;
import static io.airlift.mcp.reflection.Predicates.isCallToolRequest;
import static io.airlift.mcp.reflection.Predicates.isHttpRequest;
import static io.airlift.mcp.reflection.Predicates.isNotifier;
import static io.airlift.mcp.reflection.Predicates.isObject;
import static io.airlift.mcp.reflection.Predicates.returnsAnything;
import static io.airlift.mcp.reflection.ReflectionHelper.mapToContent;
import static io.airlift.mcp.reflection.ReflectionHelper.validate;
import static java.util.Objects.requireNonNull;

public class ToolHandlerProvider
        implements Provider<ToolEntry>
{
    private final Tool tool;
    private final Class<?> clazz;
    private final Method method;
    private final List<MethodParameter> parameters;
    private final ReturnType returnType;
    @Inject private Injector injector;
    @Inject private ObjectMapper objectMapper;

    public ToolHandlerProvider(McpTool mcpTool, Class<?> clazz, Method method, List<MethodParameter> parameters)
    {
        this.clazz = requireNonNull(clazz, "clazz is null");
        this.method = requireNonNull(method, "method is null");
        this.parameters = ImmutableList.copyOf(parameters);

        validate(method, parameters, isHttpRequest.or(isNotifier).or(isObject).or(isCallToolRequest), returnsAnything);

        tool = buildTool(mcpTool, method, parameters);

        if (void.class.equals(method.getReturnType())) {
            returnType = ReturnType.VOID;
        }
        else if (Content.class.isAssignableFrom(method.getReturnType()) || isPrimitiveType(method.getGenericReturnType())) {
            returnType = ReturnType.CONTENT;
        }
        else if (CallToolResult.class.isAssignableFrom(method.getReturnType())) {
            returnType = ReturnType.CALL_TOOL_RESULT;
        }
        else if (isSupportedType(method.getGenericReturnType())) {
            returnType = ReturnType.STRUCTURED;
        }
        else {
            throw new IllegalArgumentException("Method %s has unsupported return type: %s".formatted(method.getName(), method.getGenericReturnType()));
        }
    }

    private enum ReturnType {
        VOID,
        CALL_TOOL_RESULT,
        CONTENT,
        STRUCTURED,
    }

    @Override
    public ToolEntry get()
    {
        Object instance = injector.getInstance(clazz);
        MethodInvoker methodInvoker = new MethodInvoker(instance, method, parameters, objectMapper);

        ToolHandler toolHandler = (request, notifier, toolRequest) -> {
            Object result = methodInvoker.builder(request)
                    .withArguments(toolRequest.arguments())
                    .withNotifier(notifier)
                    .withCallToolRequest(toolRequest)
                    .invoke();
            if ((result == null) && (returnType != ReturnType.VOID)) {
                throw exception(JsonRpcErrorCode.INTERNAL_ERROR, "Tool %s returned null".formatted(method.getName()));
            }

            return switch (returnType) {
                case VOID -> new CallToolResult(ImmutableList.of());
                case CONTENT -> new CallToolResult(mapToContent(result));
                case STRUCTURED -> new CallToolResult(ImmutableList.of(mapToContent(result)), Optional.of(new StructuredContent<>(result)), false);
                case CALL_TOOL_RESULT -> (CallToolResult) result;
            };
        };

        return new ToolEntry(tool, toolHandler);
    }

    private static Tool buildTool(McpTool tool, Method method, List<MethodParameter> parameters)
    {
        Optional<String> description = tool.description().isEmpty() ? Optional.empty() : Optional.of(tool.description());
        Optional<String> title = tool.title().isEmpty() ? Optional.empty() : Optional.of(tool.title());

        Tool.ToolAnnotations toolAnnotations = new Tool.ToolAnnotations(
                title,
                tool.readOnlyHint().map(),
                tool.destructiveHint().map(),
                tool.idempotentHint().map(),
                tool.openWorldHint().map(),
                tool.returnDirect().map());

        Optional<ObjectNode> outputSchema;
        if (method.getReturnType().isRecord()) {
            JsonSchemaBuilder jsonSchemaBuilder = new JsonSchemaBuilder("Tool (return): " + tool.name());
            outputSchema = Optional.of(jsonSchemaBuilder.build(description, method.getReturnType()));
        }
        else {
            outputSchema = Optional.empty();
        }

        JsonSchemaBuilder jsonSchemaBuilder = new JsonSchemaBuilder("Tool: " + tool.name());
        ObjectNode jsonSchema = jsonSchemaBuilder.build(description, parameters);

        return new Tool(tool.name(), description, jsonSchema, outputSchema, toolAnnotations);
    }
}

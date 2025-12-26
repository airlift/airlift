package io.airlift.mcp.reflection;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Provider;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CompleteRequest.CompleteArgument;
import io.airlift.mcp.model.CompleteRequest.CompleteContext;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.ResourceTemplateValues;
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
import jakarta.servlet.http.HttpServletRequest;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.MCP_IDENTITY_ATTRIBUTE;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static java.util.Objects.requireNonNull;

public class MethodInvoker
{
    private final String methodName;
    private final List<MethodParameter> parameters;
    private final ObjectMapper objectMapper;
    private final Supplier<MethodHandle> methodHandle;

    // instance is a Provider to avoid a circular dependency problem with `McpServer` implementations
    @Inject
    public MethodInvoker(Provider<?> instance, Method method, List<MethodParameter> parameters, ObjectMapper objectMapper)
    {
        this.methodName = method.getName();
        this.parameters = ImmutableList.copyOf(parameters);
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");

        try {
            MethodType methodType = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
            MethodHandle builder = MethodHandles.lookup().findVirtual(method.getDeclaringClass(), method.getName(), methodType);
            methodHandle = Suppliers.memoize(() -> builder.bindTo(instance.get()));
        }
        catch (Exception e) {
            throw exception(e);
        }
    }

    public interface Builder
    {
        Builder withArguments(Map<String, Object> arguments);

        Builder withGetPromptRequest(GetPromptRequest getPromptRequest);

        Builder withCallToolRequest(CallToolRequest callToolRequest);

        Builder withReadResourceRequest(Resource sourceResource, ReadResourceRequest readResourceRequest);

        Builder withReadResourceTemplateRequest(ResourceTemplate sourceResourceTemplate, ReadResourceRequest readResourceRequest);

        Builder withResourceTemplateValues(ResourceTemplateValues resourceTemplateValues);

        Builder withCompleteArgument(CompleteArgument completeArgument);

        Builder withCompleteContext(CompleteContext completeContext);

        Object invoke();
    }

    public Builder builder(McpRequestContext requestContext)
    {
        return new Builder()
        {
            private Map<String, Object> arguments = ImmutableMap.of();
            private Optional<GetPromptRequest> getPromptRequest = Optional.empty();
            private Optional<CallToolRequest> callToolRequest = Optional.empty();
            private Optional<Resource> sourceResource = Optional.empty();
            private Optional<ResourceTemplate> sourceResourceTemplate = Optional.empty();
            private Optional<ReadResourceRequest> readResourceRequest = Optional.empty();
            private Optional<ResourceTemplateValues> resourceTemplateValues = Optional.empty();
            private Optional<CompleteArgument> completeArgument = Optional.empty();
            private Optional<CompleteContext> completeContext = Optional.empty();

            @Override
            public Builder withArguments(Map<String, Object> arguments)
            {
                this.arguments = ImmutableMap.copyOf(arguments);
                return this;
            }

            @Override
            public Builder withGetPromptRequest(GetPromptRequest getPromptRequest)
            {
                this.getPromptRequest = Optional.of(getPromptRequest);
                return this;
            }

            @Override
            public Builder withCallToolRequest(CallToolRequest callToolRequest)
            {
                this.callToolRequest = Optional.of(callToolRequest);
                return this;
            }

            @Override
            public Builder withReadResourceRequest(Resource sourceResource, ReadResourceRequest readResourceRequest)
            {
                this.sourceResource = Optional.of(sourceResource);
                this.readResourceRequest = Optional.of(readResourceRequest);
                return this;
            }

            @Override
            public Builder withReadResourceTemplateRequest(ResourceTemplate sourceResourceTemplate, ReadResourceRequest readResourceRequest)
            {
                this.sourceResourceTemplate = Optional.of(sourceResourceTemplate);
                this.readResourceRequest = Optional.of(readResourceRequest);
                return this;
            }

            @Override
            public Builder withResourceTemplateValues(ResourceTemplateValues resourceTemplateValues)
            {
                this.resourceTemplateValues = Optional.of(resourceTemplateValues);
                return this;
            }

            @Override
            public Builder withCompleteArgument(CompleteArgument completeArgument)
            {
                this.completeArgument = Optional.of(completeArgument);
                return this;
            }

            @Override
            public Builder withCompleteContext(CompleteContext completeContext)
            {
                this.completeContext = Optional.of(completeContext);
                return this;
            }

            @Override
            public Object invoke()
            {
                try {
                    Object[] methodArguments = parameters.stream()
                            .map(parameter -> switch (parameter) {
                                case HttpRequestParameter _ -> requestContext.request();
                                case McpRequestContextParameter _ -> requestContext;
                                case GetPromptRequestParameter _ -> getPromptRequest.orElseThrow(() -> new IllegalStateException("GetPromptRequest is required"));
                                case CallToolRequestParameter _ -> callToolRequest.orElseThrow(() -> new IllegalStateException("CallToolRequest is required"));
                                case SourceResourceParameter _ -> sourceResource.orElseThrow(() -> new IllegalStateException("SourceResource is required"));
                                case SourceResourceTemplateParameter _ -> sourceResourceTemplate.orElseThrow(() -> new IllegalStateException("SourceResourceTemplate is required"));
                                case ReadResourceRequestParameter _ -> readResourceRequest.orElseThrow(() -> new IllegalStateException("ReadResourceRequest is required"));
                                case ResourceTemplateValuesParameter _ -> resourceTemplateValues.orElseThrow(() -> new IllegalStateException("ResourceTemplateValues is required"));
                                case IdentityParameter _ -> retrieveIdentityValue(requestContext.request());
                                case CompleteArgumentParameter _ -> completeArgument.orElseThrow(() -> new IllegalStateException("CompleteArgument is required"));
                                case CompleteContextParameter _ -> completeContext.orElseThrow(() -> new IllegalStateException("CompleteContext is required"));
                                case ObjectParameter objectParameter -> valueForObjectParameter(arguments, objectParameter);
                            })
                            .toArray();

                    return methodHandle.get().invokeWithArguments(methodArguments);
                }
                catch (Throwable e) {
                    Throwable rootCause = Throwables.getRootCause(e);
                    throw switch (rootCause) {
                        case McpException mcpException -> new McpClientException(mcpException);
                        case RuntimeException runtimeException -> new McpClientException(exception(runtimeException));
                        case InvocationTargetException invocationTargetException -> {
                            Throwable targetException = invocationTargetException.getTargetException();
                            if (targetException instanceof McpException mcpException) {
                                yield mcpException;
                            }
                            yield new McpClientException(exception(targetException));
                        }
                        default -> new McpException(rootCause, new JsonRpcErrorDetail(INTERNAL_ERROR, "Failed to invoke method: " + methodName));
                    };
                }
            }
        };
    }

    private Object valueForObjectParameter(Map<String, Object> arguments, ObjectParameter objectParameter)
    {
        Object value = arguments.get(objectParameter.name());
        if (value == null) {
            if (objectParameter.required()) {
                throw new McpClientException(exception("Missing required parameter: " + objectParameter.name()));
            }
            return Optional.empty();
        }

        if (objectParameter.rawType().isRecord() || Optional.class.isAssignableFrom(objectParameter.rawType()) || objectParameter.rawType().isEnum()) {
            JavaType javaType = objectMapper.getTypeFactory().constructType(objectParameter.genericType());
            return objectMapper.convertValue(value, javaType);
        }

        return value;
    }

    private static Object retrieveIdentityValue(HttpServletRequest request)
            throws McpException
    {
        Object identity = request.getAttribute(MCP_IDENTITY_ATTRIBUTE);
        if (identity == null) {
            throw exception(INTERNAL_ERROR, "Error in request processing. MCP identity not found.");
        }
        return identity;
    }
}

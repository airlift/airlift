package io.airlift.mcp.reflection;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.mcp.McpException;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.reflection.MethodParameter.CallToolRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.GetPromptRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.HttpRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.IdentityParameter;
import io.airlift.mcp.reflection.MethodParameter.ObjectParameter;
import io.airlift.mcp.reflection.MethodParameter.ReadResourceRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.SourceResourceParameter;
import jakarta.servlet.http.HttpServletRequest;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.reference.ReferenceFilter.retrieveIdentityValue;
import static java.util.Objects.requireNonNull;

public class MethodInvoker
{
    private final String methodName;
    private final List<MethodParameter> parameters;
    private final ObjectMapper objectMapper;
    private final MethodHandle methodHandle;

    @Inject
    public MethodInvoker(Object instance, Method method, List<MethodParameter> parameters, ObjectMapper objectMapper)
    {
        this.methodName = method.getName();
        this.parameters = ImmutableList.copyOf(parameters);
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");

        try {
            MethodType methodType = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
            MethodHandle builder = MethodHandles.lookup().findVirtual(method.getDeclaringClass(), method.getName(), methodType);
            methodHandle = builder.bindTo(instance);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public interface Builder
    {
        Builder withArguments(Map<String, Object> arguments);

        Builder withGetPromptRequest(GetPromptRequest getPromptRequest);

        Builder withCallToolRequest(CallToolRequest callToolRequest);

        Builder withReadResourceRequest(Resource sourceResource, ReadResourceRequest readResourceRequest);

        Object invoke();
    }

    public Builder builder(HttpServletRequest request)
    {
        return new Builder()
        {
            private Map<String, Object> arguments = ImmutableMap.of();
            private Optional<GetPromptRequest> getPromptRequest = Optional.empty();
            private Optional<CallToolRequest> callToolRequest = Optional.empty();
            private Optional<Resource> sourceResource = Optional.empty();
            private Optional<ReadResourceRequest> readResourceRequest = Optional.empty();

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
            public Object invoke()
            {
                try {
                    Object[] methodArguments = parameters.stream()
                            .map(parameter -> switch (parameter) {
                                case HttpRequestParameter _ -> request;
                                case GetPromptRequestParameter _ -> getPromptRequest.orElseThrow(() -> new IllegalStateException("GetPromptRequest is required"));
                                case CallToolRequestParameter _ -> callToolRequest.orElseThrow(() -> new IllegalStateException("CallToolRequest is required"));
                                case SourceResourceParameter _ -> sourceResource.orElseThrow(() -> new IllegalStateException("SourceResource is required"));
                                case ReadResourceRequestParameter _ -> readResourceRequest.orElseThrow(() -> new IllegalStateException("ReadResourceRequest is required"));
                                case IdentityParameter _ -> retrieveIdentityValue(request);
                                case ObjectParameter objectParameter -> valueForObjectParameter(arguments, objectParameter);
                            })
                            .toArray();

                    return methodHandle.invokeWithArguments(methodArguments);
                }
                catch (Throwable e) {
                    Throwable rootCause = Throwables.getRootCause(e);
                    throw switch (rootCause) {
                        case McpException mcpException -> mcpException;
                        case RuntimeException runtimeException -> runtimeException;
                        case InvocationTargetException invocationTargetException -> {
                            Throwable targetException = invocationTargetException.getTargetException();
                            if (targetException instanceof McpException mcpException) {
                                yield mcpException;
                            }
                            yield new RuntimeException(targetException);
                        }
                        default -> new RuntimeException("Failed to invoke method: " + methodName, rootCause);
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
                throw exception(INVALID_REQUEST, "Missing required parameter: " + objectParameter.name());
            }
            return Optional.empty();
        }

        if (objectParameter.rawType().isRecord()) {
            JavaType javaType = objectMapper.getTypeFactory().constructType(objectParameter.genericType());
            return objectMapper.convertValue(value, javaType);
        }

        return value;
    }
}

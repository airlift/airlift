package io.airlift.mcp.reflection;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.handler.RequestContext;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CompletionRequest;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.reflection.MethodParameter.CallToolRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.CompletionRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.GetPromptRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.HttpRequestParameter;
import io.airlift.mcp.reflection.MethodParameter.JaxrsContextParameter;
import io.airlift.mcp.reflection.MethodParameter.NotifierParameter;
import io.airlift.mcp.reflection.MethodParameter.ObjectParameter;
import io.airlift.mcp.reflection.MethodParameter.SessionIdParameter;
import jakarta.ws.rs.WebApplicationException;
import org.glassfish.jersey.server.ContainerRequest;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static io.airlift.jsonrpc.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.McpException.exception;
import static java.util.Objects.requireNonNull;

public class MethodInvoker
{
    private final Object instance;
    private final Method method;
    private final List<MethodParameter> parameters;
    private final ObjectMapper objectMapper;
    private final JerseyContextEmulation jerseyContextEmulation;

    public MethodInvoker(Object instance, Method method, List<MethodParameter> parameters, ObjectMapper objectMapper, JerseyContextEmulation jerseyContextEmulation)
    {
        this.instance = requireNonNull(instance, "instance is null");
        this.method = requireNonNull(method, "method is null");
        this.parameters = ImmutableList.copyOf(parameters);
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        this.jerseyContextEmulation = requireNonNull(jerseyContextEmulation, "jerseyContextEmulation is null");

        // call the supplier for any jaxrsContextParameters so that they are initialized
        // most implementations should use a memoized supplier
        parameters.stream()
                .flatMap(parameter -> (parameter instanceof JaxrsContextParameter jaxrsContextParameter) ? Stream.of(jaxrsContextParameter) : Stream.empty())
                .forEach(jaxrsContextParameter -> jaxrsContextParameter.contextResolverSupplier().apply(jerseyContextEmulation).get());
    }

    public interface Builder
    {
        Builder withArguments(Map<String, Object> arguments);

        Builder withCompletionRequest(CompletionRequest completion);

        Builder withGetPromptRequest(GetPromptRequest getPromptRequest);

        Builder withCallToolRequest(CallToolRequest callToolRequest);

        Builder withNotifier(McpNotifier notifier);

        Object invoke();
    }

    public Builder builder(RequestContext requestContext)
    {
        if (!(requestContext.request() instanceof ContainerRequest)) {
            throw new IllegalArgumentException("request is not a ContainerRequest");
        }

        return new Builder()
        {
            private Map<String, Object> arguments = ImmutableMap.of();
            private Optional<CompletionRequest> completion = Optional.empty();
            private Optional<McpNotifier> notifier = Optional.empty();
            private Optional<GetPromptRequest> getPromptRequest = Optional.empty();
            private Optional<CallToolRequest> callToolRequest = Optional.empty();

            @Override
            public Builder withArguments(Map<String, Object> arguments)
            {
                this.arguments = ImmutableMap.copyOf(arguments);
                return this;
            }

            @Override
            public Builder withCompletionRequest(CompletionRequest completion)
            {
                this.completion = Optional.of(completion);
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
            public Builder withNotifier(McpNotifier notifier)
            {
                this.notifier = Optional.of(notifier);
                return this;
            }

            @Override
            public Object invoke()
            {
                try {
                    Object[] methodArguments = parameters.stream()
                            .map(parameter -> switch (parameter) {
                                case HttpRequestParameter _ -> requestContext.request();
                                case SessionIdParameter _ -> requestContext.sessionId();
                                case JaxrsContextParameter jaxrsContextParameter -> jaxrsContextParameter.contextResolverSupplier().apply(jerseyContextEmulation).get().resolve(requestContext);
                                case NotifierParameter _ -> notifier.orElseThrow(() -> new IllegalArgumentException("Notifier is required"));
                                case CompletionRequestParameter _ -> completion.orElseThrow(() -> new IllegalArgumentException("Completion is required"));
                                case GetPromptRequestParameter _ -> getPromptRequest.orElseThrow(() -> new IllegalArgumentException("GetPromptRequest is required"));
                                case CallToolRequestParameter _ -> callToolRequest.orElseThrow(() -> new IllegalArgumentException("CallToolRequest is required"));
                                case ObjectParameter objectParameter -> valueForObjectParameter(arguments, objectParameter);
                            })
                            .toArray();

                    return method.invoke(instance, methodArguments);
                }
                catch (McpException | WebApplicationException e) {
                    throw e;
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
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

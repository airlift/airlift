package io.airlift.mcp.reflection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import io.airlift.jsonrpc.model.JsonRpcErrorCode;
import io.airlift.mcp.McpCompletion;
import io.airlift.mcp.handler.CompletionEntry;
import io.airlift.mcp.handler.CompletionHandler;
import io.airlift.mcp.model.Completion;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.reflection.Predicates.isCompletionRequest;
import static io.airlift.mcp.reflection.Predicates.isNotifier;
import static io.airlift.mcp.reflection.Predicates.isRequestParameter;
import static io.airlift.mcp.reflection.Predicates.returnsOptionalCompletion;
import static io.airlift.mcp.reflection.Predicates.returnsOptionalListOfString;
import static io.airlift.mcp.reflection.ReflectionHelper.listArgument;
import static io.airlift.mcp.reflection.ReflectionHelper.validate;
import static java.util.Objects.requireNonNull;

public class CompletionHandlerProvider
        implements Provider<CompletionEntry>
{
    private final Class<?> clazz;
    private final Method method;
    private final List<MethodParameter> parameters;
    private final boolean isStringListResult;
    private final String name;
    @Inject private Injector injector;
    @Inject private ObjectMapper objectMapper;
    @Inject private JerseyContextEmulation jerseyContextEmulation;

    public CompletionHandlerProvider(McpCompletion mcpCompletion, Class<?> clazz, Method method, List<MethodParameter> parameters)
    {
        this.clazz = requireNonNull(clazz, "clazz is null");
        this.method = requireNonNull(method, "method is null");
        this.parameters = ImmutableList.copyOf(parameters);
        this.name = mcpCompletion.name();

        validate(method, parameters, isRequestParameter.or(isNotifier).or(isCompletionRequest), returnsOptionalCompletion.or(returnsOptionalListOfString));

        isStringListResult = listArgument(method.getGenericReturnType()).map(type -> type.equals(String.class)).orElse(false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletionEntry get()
    {
        Object instance = injector.getInstance(clazz);
        MethodInvoker methodInvoker = new MethodInvoker(instance, method, parameters, objectMapper, jerseyContextEmulation);
        CompletionHandler completionHandler = (requestContext, notifier, completionRequest) -> {
            Object result = methodInvoker.builder(requestContext)
                    .withCompletionRequest(completionRequest)
                    .withNotifier(notifier)
                    .invoke();
            if (result == null) {
                throw exception(JsonRpcErrorCode.INTERNAL_ERROR, "Completion %s returned null".formatted(method.getName()));
            }

            if (isStringListResult) {
                return Optional.of(new Completion((List<String>) result));
            }

            return (Optional<Completion>) result;
        };

        return new CompletionEntry(name, completionHandler);
    }
}

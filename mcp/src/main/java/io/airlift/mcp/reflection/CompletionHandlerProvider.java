package io.airlift.mcp.reflection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import io.airlift.mcp.McpPromptCompletion;
import io.airlift.mcp.McpResourceTemplateCompletion;
import io.airlift.mcp.handler.CompletionEntry;
import io.airlift.mcp.handler.CompletionHandler;
import io.airlift.mcp.model.CompleteReference;
import io.airlift.mcp.model.CompleteReference.PromptReference;
import io.airlift.mcp.model.CompleteReference.ResourceReference;
import io.airlift.mcp.model.CompleteRequest.CompleteContext;
import io.airlift.mcp.model.CompleteResult;
import io.airlift.mcp.model.CompleteResult.CompleteCompletion;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.reflection.Predicates.isCompleteArgument;
import static io.airlift.mcp.reflection.Predicates.isCompleteContext;
import static io.airlift.mcp.reflection.Predicates.isHttpRequestOrContext;
import static io.airlift.mcp.reflection.Predicates.isIdentity;
import static io.airlift.mcp.reflection.Predicates.returnsCompleteCompletion;
import static io.airlift.mcp.reflection.Predicates.returnsStringList;
import static io.airlift.mcp.reflection.ReflectionHelper.validate;
import static java.util.Objects.requireNonNull;

public class CompletionHandlerProvider
        implements Provider<CompletionEntry>
{
    private final CompleteReference completeReference;
    private final Class<?> clazz;
    private final Method method;
    private final List<MethodParameter> parameters;
    private Injector injector;
    private ObjectMapper objectMapper;

    public CompletionHandlerProvider(McpPromptCompletion mcpPromptCompletion, Class<?> clazz, Method method, List<MethodParameter> parameters)
    {
        this(buildPromptReference(mcpPromptCompletion), clazz, method, parameters);
    }

    public CompletionHandlerProvider(McpResourceTemplateCompletion mcpResourceCompletion, Class<?> clazz, Method method, List<MethodParameter> parameters)
    {
        this(new ResourceReference(mcpResourceCompletion.uriTemplate()), clazz, method, parameters);
    }

    private CompletionHandlerProvider(CompleteReference completeReference, Class<?> clazz, Method method, List<MethodParameter> parameters)
    {
        this.completeReference = completeReference;
        this.clazz = requireNonNull(clazz, "clazz is null");
        this.method = requireNonNull(method, "method is null");
        this.parameters = ImmutableList.copyOf(parameters);

        validate(method, parameters, isHttpRequestOrContext.or(isIdentity).or(isCompleteArgument).or(isCompleteContext), returnsCompleteCompletion.or(returnsStringList));
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

    @SuppressWarnings("unchecked")
    @Override
    public CompletionEntry get()
    {
        Provider<?> instance = injector.getProvider(clazz);
        MethodInvoker methodInvoker = new MethodInvoker(instance, method, parameters, objectMapper);

        CompleteContext emptyContext = new CompleteContext(ImmutableMap.of());
        CompleteResult emptyResult = new CompleteResult(new CompleteCompletion(ImmutableList.of(), OptionalInt.empty(), Optional.empty()));

        CompletionHandler handler = (requestContext, completeRequest) -> {
            if (!completeRequest.ref().getClass().equals(completeReference.getClass())) {
                return emptyResult;
            }

            Object result = methodInvoker.builder(requestContext)
                    .withCompleteArgument(completeRequest.argument())
                    .withCompleteContext(completeRequest.context().orElse(emptyContext))
                    .invoke();

            if (result == null) {
                throw exception(INTERNAL_ERROR, "CompletionHandler %s returned null".formatted(method.getName()));
            }

            if (result instanceof CompleteCompletion completeCompletion) {
                return new CompleteResult(completeCompletion);
            }

            List<String> values = (List<String>) result;
            return new CompleteResult(new CompleteCompletion(values, OptionalInt.empty(), Optional.empty()));
        };

        return new CompletionEntry(completeReference, handler);
    }

    private static PromptReference buildPromptReference(McpPromptCompletion mcpPromptCompletion)
    {
        Optional<String> title = mcpPromptCompletion.title().isEmpty() ? Optional.empty() : Optional.of(mcpPromptCompletion.title());
        return new PromptReference(mcpPromptCompletion.name(), title);
    }
}

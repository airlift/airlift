package io.airlift.mcp.reflection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import io.airlift.jsonrpc.model.JsonRpcErrorCode;
import io.airlift.mcp.McpPrompt;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.PromptHandler;
import io.airlift.mcp.model.Content;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.GetPromptResult.PromptMessage;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Role;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.reflection.Predicates.isGetPromptRequest;
import static io.airlift.mcp.reflection.Predicates.isHttpRequestOrSessonId;
import static io.airlift.mcp.reflection.Predicates.isNotifier;
import static io.airlift.mcp.reflection.Predicates.isString;
import static io.airlift.mcp.reflection.Predicates.returnsGetPromptResult;
import static io.airlift.mcp.reflection.Predicates.returnsString;
import static io.airlift.mcp.reflection.ReflectionHelper.mapToContent;
import static io.airlift.mcp.reflection.ReflectionHelper.validate;
import static java.util.Objects.requireNonNull;

public class PromptHandlerProvider
        implements Provider<PromptEntry>
{
    private final Prompt prompt;
    private final Class<?> clazz;
    private final Method method;
    private final List<MethodParameter> parameters;
    private final Role role;
    private final boolean isGetPromptResult;
    @Inject private Injector injector;
    @Inject private ObjectMapper objectMapper;

    public PromptHandlerProvider(McpPrompt mcpPrompt, Class<?> clazz, Method method, List<MethodParameter> parameters, Role role)
    {
        this.clazz = requireNonNull(clazz, "clazz is null");
        this.method = requireNonNull(method, "method is null");
        this.parameters = ImmutableList.copyOf(parameters);
        this.role = requireNonNull(role, "role is null");

        validate(method, parameters, isHttpRequestOrSessonId.or(isNotifier).or(isString).or(isGetPromptRequest), returnsString.or(returnsGetPromptResult));

        prompt = buildPrompt(mcpPrompt, parameters);
        isGetPromptResult = GetPromptResult.class.isAssignableFrom(method.getReturnType());
    }

    @Override
    public PromptEntry get()
    {
        Object instance = injector.getInstance(clazz);
        MethodInvoker methodInvoker = new MethodInvoker(instance, method, parameters, objectMapper);

        PromptHandler promptHandler = (request, sessionId, notifier, promptRequest) -> {
            Object result = methodInvoker.builder(request, sessionId)
                    .withArguments(promptRequest.arguments())
                    .withNotifier(notifier)
                    .withGetPromptRequest(promptRequest)
                    .invoke();
            if (result == null) {
                throw exception(JsonRpcErrorCode.INTERNAL_ERROR, "Prompt %s returned null".formatted(method.getName()));
            }

            if (isGetPromptResult) {
                return (GetPromptResult) result;
            }

            Content content = mapToContent(result);
            return new GetPromptResult(prompt.description(), ImmutableList.of(new PromptMessage(role, content)));
        };

        return new PromptEntry(prompt, promptHandler);
    }

    private static Prompt buildPrompt(McpPrompt prompt, List<MethodParameter> parameters)
    {
        Optional<String> description = prompt.description().isEmpty() ? Optional.empty() : Optional.of(prompt.description());
        return new Prompt(prompt.name(), description, prompt.role(), toPromptArguments(parameters));
    }

    private static List<Prompt.Argument> toPromptArguments(List<MethodParameter> parameters)
    {
        return parameters
                .stream()
                .flatMap(methodParameter -> (methodParameter instanceof MethodParameter.ObjectParameter objectParameter) ? Stream.of(objectParameter) : Stream.empty())
                .map(objectParameter -> new Prompt.Argument(objectParameter.name(), objectParameter.description(), objectParameter.required()))
                .collect(toImmutableList());
    }
}

package io.airlift.mcp.reflection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import io.airlift.mcp.McpPrompt;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.PromptHandler;
import io.airlift.mcp.model.Content;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.GetPromptResult.PromptMessage;
import io.airlift.mcp.model.JsonRpcErrorCode;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Role;
import io.airlift.mcp.reflection.MethodParameter.ObjectParameter;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.reflection.Predicates.isGetPromptRequest;
import static io.airlift.mcp.reflection.Predicates.isHttpRequestOrContext;
import static io.airlift.mcp.reflection.Predicates.isIdentity;
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
    private final List<String> icons;
    private Injector injector;
    private ObjectMapper objectMapper;

    public PromptHandlerProvider(McpPrompt mcpPrompt, Class<?> clazz, Method method, List<MethodParameter> parameters)
    {
        this.clazz = requireNonNull(clazz, "clazz is null");
        this.method = requireNonNull(method, "method is null");
        this.parameters = ImmutableList.copyOf(parameters);
        this.role = mcpPrompt.role();
        icons = ImmutableList.copyOf(mcpPrompt.icons());

        validate(method, parameters, isHttpRequestOrContext.or(isIdentity).or(isString).or(isGetPromptRequest), returnsString.or(returnsGetPromptResult));

        prompt = buildPrompt(mcpPrompt, parameters);
        isGetPromptResult = GetPromptResult.class.isAssignableFrom(method.getReturnType());
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

    @Override
    public PromptEntry get()
    {
        Provider<?> instance = injector.getProvider(clazz);
        IconHelper iconHelper = injector.getInstance(IconHelper.class);
        MethodInvoker methodInvoker = new MethodInvoker(instance, method, parameters, objectMapper);

        PromptHandler promptHandler = (requestContext, promptRequest) -> {
            Object result = methodInvoker.builder(requestContext)
                    .withArguments(promptRequest.arguments())
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

        return new PromptEntry(prompt.withIcons(iconHelper.mapIcons(icons)), promptHandler);
    }

    private static Prompt buildPrompt(McpPrompt prompt, List<MethodParameter> parameters)
    {
        Optional<String> description = prompt.description().isEmpty() ? Optional.empty() : Optional.of(prompt.description());

        return new Prompt(prompt.name(), description, Optional.of(prompt.role()), toPromptArguments(parameters));
    }

    private static List<Prompt.Argument> toPromptArguments(List<MethodParameter> parameters)
    {
        return parameters
                .stream()
                .flatMap(methodParameter -> (methodParameter instanceof ObjectParameter objectParameter) ? Stream.of(objectParameter) : Stream.empty())
                .map(objectParameter -> new Prompt.Argument(objectParameter.name(), objectParameter.description(), objectParameter.required()))
                .collect(toImmutableList());
    }
}

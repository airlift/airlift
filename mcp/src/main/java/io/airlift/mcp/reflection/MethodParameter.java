package io.airlift.mcp.reflection;

import io.airlift.mcp.reflection.JerseyContextEmulation.InternalContextResolver;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public sealed interface MethodParameter
{
    record HttpRequestParameter()
            implements MethodParameter
    {
        public static final HttpRequestParameter INSTANCE = new HttpRequestParameter();
    }

    record SessionIdParameter()
            implements MethodParameter
    {
        public static final SessionIdParameter INSTANCE = new SessionIdParameter();
    }

    record CompletionRequestParameter()
            implements MethodParameter
    {
        public static final CompletionRequestParameter INSTANCE = new CompletionRequestParameter();
    }

    record GetPromptRequestParameter()
            implements MethodParameter
    {
        public static final GetPromptRequestParameter INSTANCE = new GetPromptRequestParameter();
    }

    record CallToolRequestParameter()
            implements MethodParameter
    {
        public static final CallToolRequestParameter INSTANCE = new CallToolRequestParameter();
    }

    record NotifierParameter()
            implements MethodParameter
    {
        public static final NotifierParameter INSTANCE = new NotifierParameter();
    }

    record SourceResourceParameter()
            implements MethodParameter
    {
        public static final SourceResourceParameter INSTANCE = new SourceResourceParameter();
    }

    record SourceResourceTemplateParameter()
            implements MethodParameter
    {
        public static final SourceResourceTemplateParameter INSTANCE = new SourceResourceTemplateParameter();
    }

    record ReadResourceRequestParameter()
            implements MethodParameter
    {
        public static final ReadResourceRequestParameter INSTANCE = new ReadResourceRequestParameter();
    }

    record PathTemplateValuesParameter()
            implements MethodParameter
    {
        public static final PathTemplateValuesParameter INSTANCE = new PathTemplateValuesParameter();
    }

    record JaxrsContextParameter(Function<JerseyContextEmulation, Supplier<InternalContextResolver>> contextResolverSupplier)
            implements MethodParameter
    {
        public JaxrsContextParameter
        {
            requireNonNull(contextResolverSupplier, "contextResolverSupplier is null");
        }
    }

    record ObjectParameter(
            String name,
            Class<?> rawType,
            Type genericType,
            Optional<String> description,
            boolean required)
            implements MethodParameter
    {
        public ObjectParameter
        {
            requireNonNull(name, "name is null");
            requireNonNull(rawType, "rawType is null");
            requireNonNull(genericType, "genericType is null");
            requireNonNull(description, "description is null");
        }
    }
}

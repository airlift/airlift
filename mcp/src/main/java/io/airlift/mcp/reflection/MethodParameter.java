package io.airlift.mcp.reflection;

import java.lang.reflect.Type;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public sealed interface MethodParameter
{
    record HttpRequestParameter()
            implements MethodParameter
    {
        public static final HttpRequestParameter INSTANCE = new HttpRequestParameter();
    }

    record McpRequestContextParameter()
            implements MethodParameter
    {
        public static final McpRequestContextParameter INSTANCE = new McpRequestContextParameter();
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

    record ResourceTemplateValuesParameter()
            implements MethodParameter
    {
        public static final ResourceTemplateValuesParameter INSTANCE = new ResourceTemplateValuesParameter();
    }

    record ReadResourceRequestParameter()
            implements MethodParameter
    {
        public static final ReadResourceRequestParameter INSTANCE = new ReadResourceRequestParameter();
    }

    record IdentityParameter()
            implements MethodParameter
    {
        public static final IdentityParameter INSTANCE = new IdentityParameter();
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

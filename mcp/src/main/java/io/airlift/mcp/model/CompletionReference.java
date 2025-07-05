package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

public sealed interface CompletionReference
{
    @JsonProperty
    String type();

    record Prompt(String name)
            implements CompletionReference
    {
        public static final String TYPE = "ref/prompt";

        public Prompt
        {
            requireNonNull(name, "name is null");
        }

        @Override
        public String type()
        {
            return TYPE;
        }
    }

    record Resource(String uri)
            implements CompletionReference
    {
        public static final String TYPE = "ref/resource";

        public Resource
        {
            requireNonNull(uri, "uri is null");
        }

        @Override
        public String type()
        {
            return TYPE;
        }
    }
}

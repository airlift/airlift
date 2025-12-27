package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public sealed interface CompleteReference
{
    record PromptReference(String name, Optional<String> title)
            implements CompleteReference
    {
        public PromptReference
        {
            requireNonNull(name, "name is null");
            requireNonNull(title, "title is null");
        }
    }

    record ResourceReference(String uri)
            implements CompleteReference
    {
        public ResourceReference
        {
            requireNonNull(uri, "uri is null");
        }
    }
}

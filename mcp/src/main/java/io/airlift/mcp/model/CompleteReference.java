package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public sealed interface CompleteReference
{
    record PromptReference(String name, Optional<String> title)
            implements CompleteReference
    {
        public PromptReference
        {
            requireNonNull(name, "name is null");
            title = requireNonNullElse(title, Optional.empty());
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

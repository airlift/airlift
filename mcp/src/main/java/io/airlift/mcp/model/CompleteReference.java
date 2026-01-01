package io.airlift.mcp.model;

import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public sealed interface CompleteReference
{
    record PromptReference(String name, Optional<String> title)
            implements CompleteReference
    {
        public PromptReference
        {
            requireNonNull(name, "name is null");
            title = firstNonNull(title, Optional.empty());
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

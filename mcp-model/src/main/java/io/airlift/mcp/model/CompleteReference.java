package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public sealed interface CompleteReference
{
    @JsonIgnore
    String sortValue();

    record PromptReference(String name, Optional<String> title)
            implements CompleteReference
    {
        public PromptReference
        {
            requireNonNull(name, "name is null");
            title = requireNonNullElse(title, Optional.empty());
        }

        @Override
        public String sortValue()
        {
            return name;
        }
    }

    record ResourceReference(String uri)
            implements CompleteReference
    {
        public ResourceReference
        {
            requireNonNull(uri, "uri is null");
        }

        @Override
        public String sortValue()
        {
            return uri;
        }
    }
}

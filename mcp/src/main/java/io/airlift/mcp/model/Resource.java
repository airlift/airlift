package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record Resource(String name, String uri, Optional<String> description, String mimeType, Optional<Long> size, Optional<Annotations> annotations)
{
    public record Annotations(List<Role> audience, Optional<Double> priority)
    {
        public static final Annotations EMPTY = new Annotations(ImmutableList.of(), Optional.empty());

        public Annotations
        {
            audience = ImmutableList.copyOf(audience);
            requireNonNull(priority, "priority is null");
        }
    }

    public Resource
    {
        requireNonNull(name, "name is null");
        requireNonNull(uri, "uri is null");
        requireNonNull(description, "description is null");
        requireNonNull(mimeType, "mimeType is null");
        requireNonNull(size, "size is null");
        requireNonNull(annotations, "annotations is null");
    }
}

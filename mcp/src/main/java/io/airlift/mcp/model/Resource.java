package io.airlift.mcp.model;

import java.util.Optional;
import java.util.OptionalLong;

import static java.util.Objects.requireNonNull;

public record Resource(String name, String uri, Optional<String> description, String mimeType, OptionalLong size, Optional<Annotations> annotations)
{
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

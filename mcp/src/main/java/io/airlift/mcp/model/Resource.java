package io.airlift.mcp.model;

import java.util.Optional;
import java.util.OptionalLong;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record Resource(String name, String uri, Optional<String> description, String mimeType, OptionalLong size, Optional<Annotations> annotations)
{
    public Resource
    {
        requireNonNull(name, "name is null");
        requireNonNull(uri, "uri is null");
        description = firstNonNull(description, Optional.empty());
        requireNonNull(mimeType, "mimeType is null");
        size = firstNonNull(size, OptionalLong.empty());
        annotations = firstNonNull(annotations, Optional.empty());
    }
}

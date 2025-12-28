package io.airlift.mcp.model;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record Resource(String name, String uri, Optional<String> description, String mimeType, OptionalLong size, Optional<Annotations> annotations, Optional<List<Icon>> icons)
{
    public Resource
    {
        requireNonNull(name, "name is null");
        requireNonNull(uri, "uri is null");
        description = firstNonNull(description, Optional.empty());
        requireNonNull(mimeType, "mimeType is null");
        size = firstNonNull(size, OptionalLong.empty());
        annotations = firstNonNull(annotations, Optional.empty());
        icons = firstNonNull(icons, Optional.empty());
    }

    public Resource(String name, String uri, Optional<String> description, String mimeType, OptionalLong size, Optional<Annotations> annotations)
    {
        this(name, uri, description, mimeType, size, annotations, Optional.empty());
    }

    public Resource withIcons(Optional<List<Icon>> icons)
    {
        return new Resource(name, uri, description, mimeType, size, annotations, icons);
    }

    public Resource withoutIcons()
    {
        return new Resource(name, uri, description, mimeType, size, annotations, Optional.empty());
    }
}

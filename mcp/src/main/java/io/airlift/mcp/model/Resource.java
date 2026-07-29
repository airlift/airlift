package io.airlift.mcp.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record Resource(String name, String uri, Optional<String> description, String mimeType, OptionalLong size, Optional<Annotations> annotations, Optional<List<Icon>> icons, Optional<Map<String, Object>> meta)
        implements Meta<Resource>
{
    public Resource
    {
        requireNonNull(name, "name is null");
        requireNonNull(uri, "uri is null");
        description = requireNonNullElse(description, Optional.empty());
        requireNonNull(mimeType, "mimeType is null");
        size = requireNonNullElse(size, OptionalLong.empty());
        annotations = requireNonNullElse(annotations, Optional.empty());
        icons = requireNonNullElse(icons, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
    }

    @Override
    public Resource withMeta(Map<String, Object> meta)
    {
        return new Resource(name, uri, description, mimeType, size, annotations, icons, Optional.of(meta));
    }

    public Resource withIcons(Optional<List<Icon>> icons)
    {
        return new Resource(name, uri, description, mimeType, size, annotations, icons, meta);
    }

    public Resource withoutIcons()
    {
        return new Resource(name, uri, description, mimeType, size, annotations, Optional.empty(), meta);
    }
}

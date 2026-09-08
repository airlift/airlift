package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record ResourceTemplate(String name, String uriTemplate, Optional<String> description, String mimeType, Optional<Annotations> annotations, Optional<List<Icon>> icons, Optional<Map<String, Object>> meta)
        implements Meta<ResourceTemplate>
{
    public ResourceTemplate
    {
        requireNonNull(name, "name is null");
        requireNonNull(uriTemplate, "uriTemplate is null");
        description = requireNonNullElse(description, Optional.empty());
        requireNonNull(mimeType, "mimeType is null");
        annotations = requireNonNullElse(annotations, Optional.empty());
        icons = requireNonNullElse(icons, Optional.<List<Icon>>empty()).map(ImmutableList::copyOf);
        meta = normalize(meta);
    }

    @Override
    public ResourceTemplate withMeta(Map<String, Object> meta)
    {
        return new ResourceTemplate(name, uriTemplate, description, mimeType, annotations, icons, Optional.of(meta));
    }

    public ResourceTemplate withIcons(Optional<List<Icon>> icons)
    {
        return new ResourceTemplate(name, uriTemplate, description, mimeType, annotations, icons, meta);
    }

    public ResourceTemplate withoutIcons()
    {
        return new ResourceTemplate(name, uriTemplate, description, mimeType, annotations, Optional.empty(), meta);
    }
}

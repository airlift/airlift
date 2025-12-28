package io.airlift.mcp.model;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record ResourceTemplate(String name, String uriTemplate, Optional<String> description, String mimeType, Optional<Annotations> annotations, Optional<List<Icon>> icons)
{
    public ResourceTemplate
    {
        requireNonNull(name, "name is null");
        requireNonNull(uriTemplate, "uriTemplate is null");
        description = firstNonNull(description, Optional.empty());
        requireNonNull(mimeType, "mimeType is null");
        annotations = firstNonNull(annotations, Optional.empty());
        icons = firstNonNull(icons, Optional.empty());
    }

    public ResourceTemplate(String name, String uriTemplate, Optional<String> description, String mimeType, Optional<Annotations> annotations)
    {
        this(name, uriTemplate, description, mimeType, annotations, Optional.empty());
    }

    public ResourceTemplate withIcons(Optional<List<Icon>> icons)
    {
        return new ResourceTemplate(name, uriTemplate, description, mimeType, annotations, icons);
    }

    public ResourceTemplate withoutIcons()
    {
        return new ResourceTemplate(name, uriTemplate, description, mimeType, annotations, Optional.empty());
    }
}

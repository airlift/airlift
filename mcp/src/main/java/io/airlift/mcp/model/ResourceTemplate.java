package io.airlift.mcp.model;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record ResourceTemplate(String name, String uriTemplate, Optional<String> description, String mimeType, Optional<Annotations> annotations, Optional<List<Icon>> icons)
{
    public ResourceTemplate
    {
        requireNonNull(name, "name is null");
        requireNonNull(uriTemplate, "uriTemplate is null");
        description = requireNonNullElse(description, Optional.empty());
        requireNonNull(mimeType, "mimeType is null");
        annotations = requireNonNullElse(annotations, Optional.empty());
        icons = requireNonNullElse(icons, Optional.empty());
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

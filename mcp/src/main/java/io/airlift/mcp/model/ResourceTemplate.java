package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ResourceTemplate(String name, String uriTemplate, Optional<String> description, String mimeType, Optional<Long> size, Optional<Resource.Annotations> annotations)
{
    public ResourceTemplate
    {
        requireNonNull(name, "name is null");
        requireNonNull(uriTemplate, "uriTemplate is null");
        requireNonNull(description, "description is null");
        requireNonNull(mimeType, "mimeType is null");
        requireNonNull(size, "size is null");
        requireNonNull(annotations, "annotations is null");
    }

    public static ResourceTemplate map(Resource resource)
    {
        return new ResourceTemplate(
                resource.name(),
                resource.uri(),
                resource.description(),
                resource.mimeType(),
                resource.size(),
                resource.annotations());
    }
}

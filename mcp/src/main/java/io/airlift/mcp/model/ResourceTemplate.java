package io.airlift.mcp.model;

import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record ResourceTemplate(String name, String uriTemplate, Optional<String> description, String mimeType, Optional<Annotations> annotations)
{
    public ResourceTemplate
    {
        requireNonNull(name, "name is null");
        requireNonNull(uriTemplate, "uriTemplate is null");
        description = firstNonNull(description, Optional.empty());
        requireNonNull(mimeType, "mimeType is null");
        annotations = firstNonNull(annotations, Optional.empty());
    }
}

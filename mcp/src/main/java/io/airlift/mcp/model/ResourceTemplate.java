package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ResourceTemplate(String name, String uriTemplate, Optional<String> description, String mimeType, Optional<Annotations> annotations)
{
    public ResourceTemplate
    {
        requireNonNull(name, "name is null");
        requireNonNull(uriTemplate, "uriTemplate is null");
        requireNonNull(description, "description is null");
        requireNonNull(mimeType, "mimeType is null");
        requireNonNull(annotations, "annotations is null");
    }
}

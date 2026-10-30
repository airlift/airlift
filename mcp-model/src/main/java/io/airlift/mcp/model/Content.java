package io.airlift.mcp.model;

import java.util.Optional;
import java.util.OptionalLong;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public sealed interface Content
{
    record TextContent(String text, Optional<Annotations> annotations)
            implements Content
    {
        public TextContent
        {
            requireNonNull(text, "text is null");
            annotations = requireNonNullElse(annotations, Optional.empty());
        }

        public TextContent(String text)
        {
            this(text, Optional.empty());
        }
    }

    record ImageContent(String data, String mimeType, Optional<Annotations> annotations)
            implements Content
    {
        public ImageContent
        {
            requireNonNull(data, "data is null");
            requireNonNull(mimeType, "mimeType is null");
            annotations = requireNonNullElse(annotations, Optional.empty());
        }

        public ImageContent(String data, String mimeType)
        {
            this(data, mimeType, Optional.empty());
        }
    }

    record AudioContent(String data, String mimeType, Optional<Annotations> annotations)
            implements Content
    {
        public AudioContent
        {
            requireNonNull(data, "data is null");
            requireNonNull(mimeType, "mimeType is null");
            annotations = requireNonNullElse(annotations, Optional.empty());
        }

        public AudioContent(String data, String mimeType)
        {
            this(data, mimeType, Optional.empty());
        }
    }

    record EmbeddedResource(ResourceContents resource, Optional<Annotations> annotations)
            implements Content
    {
        public EmbeddedResource
        {
            requireNonNull(resource, "resource is null");
            annotations = requireNonNullElse(annotations, Optional.empty());
        }
    }

    record ResourceLink(String name, String uri, Optional<String> description, String mimeType, OptionalLong size, Optional<Annotations> annotations)
            implements Content
    {
        public ResourceLink
        {
            requireNonNull(name, "name is null");
            requireNonNull(uri, "uri is null");
            description = requireNonNullElse(description, Optional.empty());
            requireNonNull(mimeType, "mimeType is null");
            size = requireNonNullElse(size, OptionalLong.empty());
            annotations = requireNonNullElse(annotations, Optional.empty());
        }
    }
}

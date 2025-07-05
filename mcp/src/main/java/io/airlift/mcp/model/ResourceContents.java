package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ResourceContents(String name, String uri, String mimeType, Optional<String> text, Optional<String> blob)
{
    public ResourceContents
    {
        requireNonNull(name, "name is null");
        requireNonNull(uri, "uri is null");
        requireNonNull(mimeType, "mimeType is null");
        requireNonNull(text, "text is null");

        if (text.isEmpty() == blob.isEmpty()) {
            throw new IllegalArgumentException("Both text and blob cannot be empty or both cannot be present");
        }
    }

    public ResourceContents(String name, String uri, String mimeType, String text)
    {
        this(name, uri, mimeType, Optional.of(text), Optional.empty());
    }
}

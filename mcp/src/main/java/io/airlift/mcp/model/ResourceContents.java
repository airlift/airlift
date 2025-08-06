package io.airlift.mcp.model;

import java.util.Optional;

import static com.google.common.base.Verify.verify;
import static java.util.Objects.requireNonNull;

public record ResourceContents(Optional<String> name, String uri, String mimeType, Optional<String> text, Optional<String> blob)
{
    public ResourceContents
    {
        requireNonNull(name, "name is null");
        requireNonNull(uri, "uri is null");
        requireNonNull(mimeType, "mimeType is null");
        requireNonNull(text, "text is null");

        verify((text.isPresent() || blob.isPresent()) && (text.isPresent() != blob.isPresent()), "Only one of text or blob must be present");
    }

    public ResourceContents(String name, String uri, String mimeType, String text)
    {
        this(Optional.of(name), uri, mimeType, Optional.of(text), Optional.empty());
    }
}

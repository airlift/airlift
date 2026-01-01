package io.airlift.mcp.model;

import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Verify.verify;
import static java.util.Objects.requireNonNull;

public record ResourceContents(Optional<String> name, String uri, String mimeType, Optional<String> text, Optional<String> blob)
{
    public ResourceContents
    {
        name = firstNonNull(name, Optional.empty());
        requireNonNull(uri, "uri is null");
        requireNonNull(mimeType, "mimeType is null");
        text = firstNonNull(text, Optional.empty());
        blob = firstNonNull(blob, Optional.empty());

        verify((text.isPresent() || blob.isPresent()) && (text.isPresent() != blob.isPresent()), "Only one of text or blob must be present");
    }

    public ResourceContents(String name, String uri, String mimeType, String text)
    {
        this(Optional.of(name), uri, mimeType, Optional.of(text), Optional.empty());
    }
}

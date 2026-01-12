package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Verify.verify;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record ResourceContents(Optional<String> name, String uri, String mimeType, Optional<String> text, Optional<String> blob, Optional<Map<String, Object>> meta)
        implements Meta
{
    public ResourceContents
    {
        name = requireNonNullElse(name, Optional.empty());
        requireNonNull(uri, "uri is null");
        requireNonNull(mimeType, "mimeType is null");
        text = requireNonNullElse(text, Optional.empty());
        blob = requireNonNullElse(blob, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());

        verify((text.isPresent() || blob.isPresent()) && (text.isPresent() != blob.isPresent()), "Only one of text or blob must be present");
    }

    public ResourceContents(Optional<String> name, String uri, String mimeType, Optional<String> text, Optional<String> blob)
    {
        this(name, uri, mimeType, text, blob, Optional.empty());
    }

    public ResourceContents(String name, String uri, String mimeType, String text)
    {
        this(Optional.of(name), uri, mimeType, Optional.of(text), Optional.empty(), Optional.empty());
    }

    @Override
    public ResourceContents withMeta(Map<String, Object> meta)
    {
        return new ResourceContents(name, uri, mimeType, text, blob, Optional.ofNullable(meta));
    }
}

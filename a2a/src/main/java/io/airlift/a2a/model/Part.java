package io.airlift.a2a.model;

import java.util.Map;
import java.util.Optional;

public record Part(
        Optional<String> text,
        Optional<String> raw,
        Optional<String> url,
        Optional<String> dataUrl,
        Optional<Map<String, Object>> metadata,
        Optional<String> filename,
        Optional<String> mediaType)
        implements Metadata
{
}

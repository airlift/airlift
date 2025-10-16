package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;

public record StructuredContentResult<T>(List<Content> content, Optional<T> structuredContent, boolean isError) {
    public StructuredContentResult {
        content = ImmutableList.copyOf(content);
        requireNonNull(structuredContent, "structuredContent is null");
    }

    public StructuredContentResult(List<Content> content, T structuredContent, boolean isError) {
        this(content, Optional.ofNullable(structuredContent), isError);
    }
}

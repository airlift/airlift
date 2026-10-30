package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

public record StructuredContentResult<T>(List<Content> content, Optional<T> structuredContent, boolean isError)
{
    public StructuredContentResult
    {
        content = ImmutableList.copyOf(content);
        structuredContent = requireNonNullElse(structuredContent, Optional.empty());
    }

    public StructuredContentResult(List<Content> content, T structuredContent, boolean isError)
    {
        this(content, Optional.ofNullable(structuredContent), isError);
    }
}

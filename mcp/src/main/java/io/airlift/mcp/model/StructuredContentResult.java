package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;

public record StructuredContentResult<T>(List<Content> content, Optional<T> structuredContent, boolean isError)
{
    public StructuredContentResult
    {
        content = ImmutableList.copyOf(content);
        structuredContent = firstNonNull(structuredContent, Optional.empty());
    }

    public StructuredContentResult(List<Content> content, T structuredContent, boolean isError)
    {
        this(content, Optional.ofNullable(structuredContent), isError);
    }
}

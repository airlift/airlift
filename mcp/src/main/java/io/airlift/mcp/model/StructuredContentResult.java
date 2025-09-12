package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record StructuredContentResult<T>(List<Content> content, T structuredContent, boolean isError)
{
    public StructuredContentResult
    {
        content = ImmutableList.copyOf(content);
        requireNonNull(structuredContent, "structuredContent is null");
    }
}

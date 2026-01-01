package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record CallToolResult(List<Content> content, Optional<StructuredContent<?>> structuredContent, boolean isError)
{
    public CallToolResult
    {
        requireNonNull(content, "content is null");
        structuredContent = firstNonNull(structuredContent, Optional.empty());
    }

    public CallToolResult(Content content)
    {
        this(ImmutableList.of(content), Optional.empty(), false);
    }

    public CallToolResult(List<Content> content)
    {
        this(content, Optional.empty(), false);
    }
}

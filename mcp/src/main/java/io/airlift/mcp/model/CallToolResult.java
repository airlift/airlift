package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record CallToolResult(List<Content> content, Optional<StructuredContent<?>> structuredContent, boolean isError, Optional<Map<String, Object>> meta)
        implements Meta
{
    public CallToolResult
    {
        requireNonNull(content, "content is null");
        structuredContent = requireNonNullElse(structuredContent, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
    }

    public CallToolResult(List<Content> content, Optional<StructuredContent<?>> structuredContent, boolean isError)
    {
        this(content, structuredContent, isError, Optional.empty());
    }

    public CallToolResult(Content content)
    {
        this(ImmutableList.of(content), Optional.empty(), false, Optional.empty());
    }

    public CallToolResult(List<Content> content)
    {
        this(content, Optional.empty(), false, Optional.empty());
    }

    @Override
    public CallToolResult withMeta(Map<String, Object> meta)
    {
        return new CallToolResult(content, structuredContent, isError, Optional.of(meta));
    }
}

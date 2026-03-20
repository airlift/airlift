package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record CompleteToolResult(List<Content> content, Optional<StructuredContent<?>> structuredContent, boolean isError, Optional<Map<String, Object>> meta)
        implements Meta, CallToolResult
{
    public CompleteToolResult
    {
        requireNonNull(content, "content is null");
        structuredContent = requireNonNullElse(structuredContent, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
    }

    public CompleteToolResult(List<Content> content, Optional<StructuredContent<?>> structuredContent, boolean isError)
    {
        this(content, structuredContent, isError, Optional.empty());
    }

    public CompleteToolResult(Content content)
    {
        this(ImmutableList.of(content), Optional.empty(), false, Optional.empty());
    }

    public CompleteToolResult(List<Content> content)
    {
        this(content, Optional.empty(), false, Optional.empty());
    }

    @Override
    public CompleteToolResult withMeta(Map<String, Object> meta)
    {
        return new CompleteToolResult(content, structuredContent, isError, Optional.of(meta));
    }
}

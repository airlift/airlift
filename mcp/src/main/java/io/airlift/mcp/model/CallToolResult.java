package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

public record CallToolResult(Optional<List<Content>> content, Optional<StructuredContent<?>> structuredContent, Optional<Task> task, Optional<Boolean> isError, Optional<Map<String, Object>> meta)
        implements Meta
{
    public CallToolResult
    {
        content = requireNonNullElse(content, Optional.empty());
        structuredContent = requireNonNullElse(structuredContent, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());

        task = requireNonNullElse(task, Optional.empty());
        isError = requireNonNullElse(isError, Optional.empty());

        boolean hasContent = content.isPresent() || structuredContent.isPresent();
        boolean hasTask = task.isPresent();

        if (hasContent && hasTask) {
            throw new IllegalArgumentException("CallToolResult cannot have both content and task");
        }
    }

    public CallToolResult(Content content)
    {
        this(Optional.of(ImmutableList.of(content)), Optional.empty(), Optional.empty(), Optional.of(false), Optional.empty());
    }

    public CallToolResult(List<Content> content)
    {
        this(Optional.of(content), Optional.empty(), Optional.empty(), Optional.of(false), Optional.empty());
    }

    public CallToolResult(StructuredContent<?> structuredContent)
    {
        this(Optional.of(ImmutableList.of()), Optional.of(structuredContent), Optional.empty(), Optional.of(false), Optional.empty());
    }

    public CallToolResult(List<Content> content, Optional<StructuredContent<?>> structuredContent, boolean isError)
    {
        this(Optional.of(content), structuredContent, Optional.empty(), Optional.of(isError), Optional.empty());
    }

    public CallToolResult(Task task)
    {
        this(Optional.empty(), Optional.empty(), Optional.of(task), Optional.empty(), Optional.empty());
    }

    @Override
    public CallToolResult withMeta(Map<String, Object> meta)
    {
        return new CallToolResult(content, structuredContent, task, isError, Optional.of(meta));
    }
}

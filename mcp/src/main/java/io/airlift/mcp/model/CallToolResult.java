package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;

public record CallToolResult(Optional<List<Content>> content, Optional<StructuredContent<?>> structuredContent, Optional<Task> task, Optional<Boolean> isError)
{
    public CallToolResult
    {
        content = firstNonNull(content, Optional.empty());
        structuredContent = firstNonNull(structuredContent, Optional.empty());
        task = firstNonNull(task, Optional.empty());
        isError = firstNonNull(isError, Optional.empty());

        boolean hasContent = content.isPresent() || structuredContent.isPresent();
        boolean hasTask = task.isPresent();

        if (hasContent && hasTask) {
            throw new IllegalArgumentException("CallToolResult cannot have both content and task");
        }
    }

    public CallToolResult(Content content)
    {
        this(Optional.of(ImmutableList.of(content)), Optional.empty(), Optional.empty(), Optional.of(false));
    }

    public CallToolResult(List<Content> content)
    {
        this(Optional.of(content), Optional.empty(), Optional.empty(), Optional.of(false));
    }

    public CallToolResult(StructuredContent<?> structuredContent)
    {
        this(Optional.of(ImmutableList.of()), Optional.of(structuredContent), Optional.empty(), Optional.of(false));
    }

    public CallToolResult(List<Content> content, Optional<StructuredContent<?>> structuredContent, boolean isError)
    {
        this(Optional.of(content), structuredContent, Optional.empty(), Optional.of(isError));
    }

    public CallToolResult(Task task)
    {
        this(Optional.empty(), Optional.empty(), Optional.of(task), Optional.empty());
    }
}

package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;
import io.airlift.mcp.model.Content.TextContent;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public record CallToolResult(List<Content> content, Optional<StructuredContent<?>> structuredContent, Optional<Task> task, boolean isError)
{
    public CallToolResult
    {
        requireNonNull(content, "content is null");
        requireNonNull(structuredContent, "structuredContent is null");
        requireNonNull(task, "task is null");

        if (task.isPresent()) {
            checkArgument(content.isEmpty() && structuredContent.isEmpty(), "Content and structuredContent must be empty when task is present");
            checkArgument(!isError, "isError must be false when task is present");
        }
    }

    public static CallToolResult error(String message)
    {
        return new CallToolResult(ImmutableList.of(new TextContent(message)), Optional.empty(), true);
    }

    public CallToolResult(List<Content> content, Optional<StructuredContent<?>> structuredContent, boolean isError)
    {
        this(content, structuredContent, Optional.empty(), isError);
    }

    public CallToolResult(Task task)
    {
        this(List.of(), Optional.empty(), Optional.of(task), false);
    }

    public CallToolResult(String content)
    {
        this(ImmutableList.of(new TextContent(content)), Optional.empty(), Optional.empty(), false);
    }

    public CallToolResult(Content content)
    {
        this(ImmutableList.of(content), Optional.empty(), Optional.empty(), false);
    }

    public CallToolResult(List<Content> content)
    {
        this(content, Optional.empty(), Optional.empty(), false);
    }
}

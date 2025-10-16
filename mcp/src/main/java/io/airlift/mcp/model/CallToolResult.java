package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;

public record CallToolResult(List<Content> content, Optional<StructuredContent<?>> structuredContent, boolean isError) {
    public CallToolResult {
        requireNonNull(content, "content is null");
        requireNonNull(structuredContent, "structuredContent is null");
    }

    public CallToolResult(Content content) {
        this(ImmutableList.of(content), Optional.empty(), false);
    }

    public CallToolResult(List<Content> content) {
        this(content, Optional.empty(), false);
    }
}

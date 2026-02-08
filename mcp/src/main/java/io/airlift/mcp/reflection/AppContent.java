package io.airlift.mcp.reflection;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public record AppContent(String sourcePath, String content, Supplier<String> contentLoader)
{
    public AppContent
    {
        requireNonNull(sourcePath, "sourcePath is null");
        requireNonNull(content, "content is null");
        requireNonNull(contentLoader, "contentLoader is null");
    }
}

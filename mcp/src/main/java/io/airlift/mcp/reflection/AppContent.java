package io.airlift.mcp.reflection;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public record AppContent(String sourcePath, Supplier<String> contentLoader)
{
    public AppContent
    {
        requireNonNull(sourcePath, "sourcePath is null");
        requireNonNull(contentLoader, "contentLoader is null");
    }
}

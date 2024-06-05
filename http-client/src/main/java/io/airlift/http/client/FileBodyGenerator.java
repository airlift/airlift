package io.airlift.http.client;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public final class FileBodyGenerator
        implements BodyGenerator
{
    private final Path path;

    public FileBodyGenerator(Path path)
    {
        this.path = requireNonNull(path, "path is null");
    }

    public Path getPath()
    {
        return path;
    }
}

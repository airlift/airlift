package io.airlift.http.client;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public record FileBodyGenerator(Path path)
        implements BodyGenerator
{
    public FileBodyGenerator
    {
        requireNonNull(path, "path is null");
    }

    @SuppressWarnings("deprecation")
    @Override
    public void write(OutputStream out)
            throws Exception
    {
        Files.copy(path, out);
    }
}

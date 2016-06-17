package io.airlift.http.client;

import com.google.common.annotations.Beta;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

@Beta
public class FileBodyGenerator
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

    @Override
    public void write(OutputStream out)
            throws Exception
    {
        Files.copy(path, out);
    }
}

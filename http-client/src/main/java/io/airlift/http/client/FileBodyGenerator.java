package io.airlift.http.client;

import com.google.common.net.MediaType;

import java.nio.file.Path;

import static com.google.common.net.MediaType.OCTET_STREAM;
import static java.util.Objects.requireNonNull;

public final class FileBodyGenerator
        implements BodyGenerator
{
    private final Path path;
    private final MediaType contentType;

    public FileBodyGenerator(Path path, MediaType contentType)
    {
        this.path = requireNonNull(path, "path is null");
        this.contentType = requireNonNull(contentType, "contentType is null");
    }

    public FileBodyGenerator(Path path)
    {
        this(path, OCTET_STREAM);
    }

    public Path getPath()
    {
        return path;
    }

    public MediaType getContentType()
    {
        return contentType;
    }
}

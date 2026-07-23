package io.airlift.testing;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TempFile
        implements Closeable
{
    private final File file;
    private boolean deleted;

    public TempFile()
            throws IOException
    {
        this.file = Files.createTempFile("tmp", null).toFile();
    }

    public File file()
    {
        return file;
    }

    public Path path()
    {
        return file.toPath();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void close()
    {
        if (!deleted) {
            deleted = true;
            file.delete();
        }
    }
}

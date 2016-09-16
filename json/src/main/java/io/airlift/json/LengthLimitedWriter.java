package io.airlift.json;

import java.io.IOException;
import java.io.Writer;

import static java.util.Objects.requireNonNull;

class LengthLimitedWriter
        extends Writer
{
    private final Writer writer;
    private final int maxLength;
    private int count;

    public LengthLimitedWriter(Writer writer, int maxLength)
    {
        this.writer = requireNonNull(writer, "writer is null");
        this.maxLength = maxLength;
    }

    @Override
    public void write(char[] buffer, int offset, int length)
            throws IOException
    {
        count += length;
        if (count > maxLength) {
            throw new LengthLimitExceededException();
        }
        writer.write(buffer, offset, length);
    }

    @Override
    public void flush()
            throws IOException
    {
        writer.flush();
    }

    @Override
    public void close()
            throws IOException
    {
        writer.close();
    }

    // this needs to extend IOException so that Jackson doesn't wrap it
    public static class LengthLimitExceededException
            extends IOException
    {
    }
}

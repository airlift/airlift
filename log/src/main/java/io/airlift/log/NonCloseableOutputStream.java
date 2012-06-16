package io.airlift.log;

import java.io.IOException;
import java.io.OutputStream;

/**
 * To get around the fact that logback closes all appenders, which close their underlying streams,
 * whenever context.reset() is called
 */
public class NonCloseableOutputStream
    extends OutputStream
{
    private final OutputStream delegate;

    public NonCloseableOutputStream(OutputStream delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public void write(int b)
            throws IOException
    {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b)
            throws IOException
    {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len)
            throws IOException
    {
        delegate.write(b, off, len);
    }

    @Override
    public void flush()
            throws IOException
    {
        delegate.flush();
    }

    @Override
    public void close()
            throws IOException
    {
        // ignore
    }
}

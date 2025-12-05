package io.airlift.http.client.jetty;

import io.airlift.units.DataSize;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static java.util.Objects.requireNonNull;

public class ThrowingLimitingInputStream
        extends InputStream
{
    private final InputStream delegate;
    private final DataSize maxBytes;
    private long bytesLeft;

    public ThrowingLimitingInputStream(InputStream delegate, DataSize maxBytes)
    {
        this.delegate = requireNonNull(delegate, "delegate is null");
        this.maxBytes = requireNonNull(maxBytes, "maxBytes is null");
        this.bytesLeft = maxBytes.toBytes();
    }

    @Override
    public int read()
            throws IOException
    {
        checkRemainingBytes(1);
        int b = delegate.read();
        if (b != -1) {
            processedBytes(1);
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len)
            throws IOException
    {
        checkRemainingBytes(len);
        int read = delegate.read(b, off, len);
        if (read > 0) {
            processedBytes(read);
        }
        return read;
    }

    @Override
    public long skip(long n)
            throws IOException
    {
        checkRemainingBytes(n);
        long skipped = delegate.skip(n);
        if (skipped > 0) {
            processedBytes(skipped);
        }
        return skipped;
    }

    @Override
    public int available()
            throws IOException
    {
        return delegate.available();
    }

    @Override
    public void close()
            throws IOException
    {
        delegate.close();
    }

    @Override
    public long transferTo(OutputStream out)
            throws IOException
    {
        return delegate.transferTo(out);
    }

    private void processedBytes(long n)
    {
        bytesLeft -= n;
    }

    private void checkRemainingBytes(long n)
            throws IOException
    {
        if (bytesLeft - n < 0) {
            throw new IOException("InputStream exceeded maximum length of " + maxBytes + " [remaining: " + DataSize.succinctBytes(bytesLeft) + ", read: " + DataSize.succinctBytes(n) + "]");
        }
    }

    static InputStream limitInputStreamLength(InputStream in, DataSize maxBytes)
    {
        if (maxBytes.toBytes() == Long.MAX_VALUE) {
            return in;
        }
        return new ThrowingLimitingInputStream(in, maxBytes);
    }
}

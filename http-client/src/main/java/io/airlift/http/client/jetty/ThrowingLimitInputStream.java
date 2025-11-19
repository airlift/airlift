package io.airlift.http.client.jetty;

import io.airlift.units.DataSize;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ThrowingLimitInputStream
        extends FilterInputStream
{
    private final DataSize maxSize;
    private long bytesLeft;

    public ThrowingLimitInputStream(InputStream in, long maxBytes)
    {
        super(in);
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must be non-negative");
        }
        this.maxSize = DataSize.succinctBytes(maxBytes);
        this.bytesLeft = maxBytes;
    }

    @Override
    public int read()
            throws IOException
    {
        int b = in.read();
        if (b != -1) {
            bytesLeft--;
            checkLimit();
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len)
            throws IOException
    {
        int n = in.read(b, off, len);
        if (n > 0) {
            bytesLeft -= n;
            checkLimit();
        }
        return n;
    }

    @Override
    public long skip(long n)
            throws IOException
    {
        long skipped = in.skip(n);
        if (skipped > 0) {
            bytesLeft -= skipped;
            checkLimit();
        }
        return skipped;
    }

    private void checkLimit()
            throws IOException
    {
        if (bytesLeft < 0) {
            throw new IOException("Response size exceeded limit of " + maxSize);
        }
    }

    static InputStream limiting(InputStream in, long maxBytes)
    {
        if (maxBytes == Long.MAX_VALUE) {
            return in;
        }
        return new ThrowingLimitInputStream(in, maxBytes);
    }
}

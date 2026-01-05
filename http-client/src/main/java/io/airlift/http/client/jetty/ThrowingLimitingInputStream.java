package io.airlift.http.client.jetty;

import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.io.InputStream;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.min;
import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNull;

/**
 * An InputStream wrapper that enforces the input stream size to be within a given limit.
 * If the limit is exceeded, an IOException is thrown.
 * <p>
 * <b>Thread-safety:</b> the class is not thread-safe.
 */
class ThrowingLimitingInputStream
        extends InputStream
{
    private final InputStream delegate;
    private final long maxBytes;
    private long bytesLeft;

    public ThrowingLimitingInputStream(InputStream delegate, long maxBytes)
    {
        this.delegate = requireNonNull(delegate, "delegate is null");
        checkArgument(maxBytes >= 0, "maxBytes is negative: %s", maxBytes);
        this.maxBytes = maxBytes;
        this.bytesLeft = maxBytes;
    }

    @VisibleForTesting
    long bytesLeft()
    {
        return bytesLeft;
    }

    @Override
    public int read()
            throws IOException
    {
        checkNotExhausted();
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
        checkNotExhausted();
        int readLen = toIntExact(min(len, bytesLeft + 1));
        int read = delegate.read(b, off, readLen);
        if (read > 0) {
            processedBytes(read);
        }
        return read;
    }

    @Override
    public long skip(long n)
            throws IOException
    {
        checkNotExhausted();
        long skipLen = min(n, bytesLeft + 1);
        long skipped = delegate.skip(skipLen);
        processedBytes(skipped);
        return skipped;
    }

    @Override
    public void skipNBytes(long n)
            throws IOException
    {
        checkNotExhausted();
        super.skipNBytes(n);
    }

    @Override
    public int available()
            throws IOException
    {
        checkNotExhausted();
        // It's possible that available bytes exceed the limit.
        // Perhaps we could throw immediately in such case.
        return delegate.available();
    }

    @Override
    public void close()
            throws IOException
    {
        delegate.close();
    }

    private void processedBytes(long n)
            throws IOException
    {
        long originalBytesLeft = bytesLeft;
        bytesLeft -= n;
        if (bytesLeft < 0) {
            throw new IOException("InputStream exceeded maximum length of %s [remaining: %s, read: %s]".formatted(maxBytes, originalBytesLeft, n));
        }
    }

    private void checkNotExhausted()
            throws IOException
    {
        if (bytesLeft < 0) {
            throw new IOException("InputStream already exceeded maximum length of %s [remaining: %s]".formatted(maxBytes, bytesLeft));
        }
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("maxBytes", maxBytes)
                .add("bytesLeft", bytesLeft)
                .add("delegate", delegate)
                .toString();
    }
}

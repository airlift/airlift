package io.airlift.http.client;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkPositionIndexes;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.min;
import static java.lang.System.arraycopy;
import static java.util.Objects.requireNonNull;

@ThreadSafe
public class GatheringByteArrayInputStream
        extends InputStream
{
    @GuardedBy("this")
    private final Iterator<byte[]> buffers;
    @GuardedBy("this")
    private final byte[] singleByte = new byte[1];
    @GuardedBy("this")
    private byte[] currentBuffer = new byte[0];
    @GuardedBy("this")
    private int currentBufferPosition;
    @GuardedBy("this")
    private long remainingBytes;

    public GatheringByteArrayInputStream(List<byte[]> buffers, long totalBytes)
    {
        checkArgument(totalBytes >= 0, "totalBytes should equal to or greater than 0");

        this.buffers = requireNonNull(buffers, "buffers is null").iterator();
        this.remainingBytes = totalBytes;
    }

    @Override
    public synchronized int read(byte[] buffer)
    {
        return read(buffer, 0, buffer.length);
    }

    @Override
    public synchronized int read()
    {
        int bytes = read(singleByte);
        if (bytes == -1) {
            return -1;
        }
        return singleByte[0] & 0xFF;
    }

    @Override
    public synchronized long skip(long n)
    {
        if (n < 0) {
            return 0;
        }

        long totalSkippedBytes = min(n, remainingBytes);

        n = totalSkippedBytes;
        while (n > 0) {
            if (currentBufferPosition >= currentBuffer.length) {
                advanceCurrentBuffer();
            }
            int skippedBytes = (int) min(n, currentBuffer.length - currentBufferPosition);
            n -= skippedBytes;
            currentBufferPosition += skippedBytes;
        }
        remainingBytes -= totalSkippedBytes;
        return totalSkippedBytes;
    }

    @Override
    public synchronized int read(byte[] buffer, int offset, int length)
    {
        requireNonNull(buffer, "buffer is null");
        checkPositionIndexes(offset, offset + length, buffer.length);

        if (remainingBytes == 0) {
            return -1;
        }

        int totalReadBytes = (int) min(length, remainingBytes);

        length = totalReadBytes;
        while (length > 0) {
            if (currentBufferPosition >= currentBuffer.length) {
                advanceCurrentBuffer();
            }
            int readBytes = min(length, currentBuffer.length - currentBufferPosition);
            arraycopy(currentBuffer, currentBufferPosition, buffer, offset, readBytes);
            offset += readBytes;
            length -= readBytes;
            currentBufferPosition += readBytes;
        }
        remainingBytes -= totalReadBytes;
        return totalReadBytes;
    }

    @Override
    public void close() {}

    private synchronized void advanceCurrentBuffer()
    {
        checkState(currentBufferPosition >= currentBuffer.length, "there is still un-read space in currentBuffer");
        checkState(buffers.hasNext(), "buffers should have more data when remainingBytes is greater than 0");

        currentBuffer = buffers.next();
        currentBufferPosition = 0;
    }
}

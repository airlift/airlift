package io.airlift.http.client.jetty;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Reads a {@link ResponseBody} in place. Bulk reads and transfers copy straight out of the segments,
 * and {@link #readAllBytes()} joins the unread part of the body into one exact array.
 */
final class SegmentedInputStream
        extends InputStream
{
    private final List<byte[]> segments;
    private final int length;

    private int segmentIndex;
    private int segmentStart;
    private int position;

    SegmentedInputStream(ResponseBody body)
    {
        requireNonNull(body, "body is null");
        this.segments = body.segments();
        this.length = body.length();
    }

    @Override
    public int read()
    {
        if (position >= length) {
            return -1;
        }
        advanceSegment();
        byte value = segments.get(segmentIndex)[position - segmentStart];
        position++;
        return value & 0xFF;
    }

    @Override
    public int read(byte[] destination, int offset, int count)
    {
        Objects.checkFromIndexSize(offset, count, destination.length);
        if (count == 0) {
            return 0;
        }
        if (position >= length) {
            return -1;
        }
        int copied = 0;
        while (copied < count && position < length) {
            advanceSegment();
            byte[] segment = segments.get(segmentIndex);
            int segmentOffset = position - segmentStart;
            int segmentLength = Math.min(segment.length, length - segmentStart);
            int chunk = Math.min(count - copied, segmentLength - segmentOffset);
            System.arraycopy(segment, segmentOffset, destination, offset + copied, chunk);
            copied += chunk;
            position += chunk;
        }
        return copied;
    }

    @Override
    public byte[] readAllBytes()
    {
        return readNBytes(Integer.MAX_VALUE);
    }

    @Override
    public byte[] readNBytes(int count)
    {
        if (count < 0) {
            throw new IllegalArgumentException("count is negative");
        }
        byte[] result = new byte[Math.min(count, length - position)];
        read(result, 0, result.length);
        return result;
    }

    @Override
    public long transferTo(OutputStream output)
            throws IOException
    {
        requireNonNull(output, "output is null");
        long transferred = 0;
        while (position < length) {
            advanceSegment();
            byte[] segment = segments.get(segmentIndex);
            int segmentOffset = position - segmentStart;
            int chunk = Math.min(segment.length, length - segmentStart) - segmentOffset;
            output.write(segment, segmentOffset, chunk);
            position += chunk;
            transferred += chunk;
        }
        return transferred;
    }

    @Override
    public long skip(long count)
    {
        int skipped = (int) Math.min(Math.max(count, 0), length - position);
        position += skipped;
        return skipped;
    }

    @Override
    public int available()
    {
        return length - position;
    }

    private void advanceSegment()
    {
        while (position - segmentStart >= segments.get(segmentIndex).length) {
            segmentStart += segments.get(segmentIndex).length;
            segmentIndex++;
        }
    }
}

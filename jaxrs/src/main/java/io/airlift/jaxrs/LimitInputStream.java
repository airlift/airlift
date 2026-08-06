/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.jaxrs;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import static java.util.Objects.requireNonNull;

class LimitInputStream
        extends FilterInputStream
{
    private final long maxBytes;
    private long bytesRead;
    private long markedBytesRead = -1;

    LimitInputStream(InputStream in, long maxBytes)
    {
        super(requireNonNull(in, "in is null"));
        this.maxBytes = maxBytes;
    }

    @Override
    public int read()
            throws IOException
    {
        int b = super.read();
        if (b != -1) {
            advance(1);
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len)
            throws IOException
    {
        int n = super.read(b, off, len);
        if (n > 0) {
            advance(n);
        }
        return n;
    }

    // skipped bytes are consumed from the payload just like read ones, so they have to count
    // against the limit or a caller could skip past it
    @Override
    public long skip(long n)
            throws IOException
    {
        long skipped = super.skip(n);
        if (skipped > 0) {
            advance(skipped);
        }
        return skipped;
    }

    // without tracking the position at the mark, bytes re-read after a reset would be counted
    // twice and could trip the limit on a payload that never exceeded it
    @Override
    public synchronized void mark(int readLimit)
    {
        in.mark(readLimit);
        markedBytesRead = bytesRead;
    }

    @Override
    public synchronized void reset()
            throws IOException
    {
        if (!in.markSupported()) {
            throw new IOException("mark/reset not supported");
        }
        if (markedBytesRead == -1) {
            throw new IOException("mark not set");
        }
        in.reset();
        bytesRead = markedBytesRead;
    }

    private void advance(long n)
            throws PayloadTooLargeException
    {
        bytesRead += n;
        if (bytesRead > maxBytes) {
            throw new PayloadTooLargeException("Request payload exceeds maximum size of %d bytes".formatted(maxBytes));
        }
    }
}

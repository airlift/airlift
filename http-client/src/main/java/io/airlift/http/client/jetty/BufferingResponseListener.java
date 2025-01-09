package io.airlift.http.client.jetty;

import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.io.ByteBufferAccumulator;
import org.eclipse.jetty.io.ByteBufferInputStream;
import org.eclipse.jetty.io.ByteBufferPool;

import java.io.InputStream;
import java.nio.ByteBuffer;

import static java.util.Objects.requireNonNull;

public abstract class BufferingResponseListener
        implements Response.Listener
{
    private final ByteBufferAccumulator buffer;
    private final int maxLength;

    /**
     * Creates an instance with a default maximum length of 2 MiB.
     */
    public BufferingResponseListener(ByteBufferPool byteBufferPool)
    {
        this(byteBufferPool, 2 * 1024 * 1024);
    }

    /**
     * Creates an instance with the given maximum length
     *
     * @param maxLength the maximum length of the content
     */
    public BufferingResponseListener(ByteBufferPool byteBufferPool, int maxLength)
    {
        this.buffer = new ByteBufferAccumulator(requireNonNull(byteBufferPool, "byteBufferPool is null"), false);
        if (maxLength < 0) {
            throw new IllegalArgumentException("Invalid max length " + maxLength);
        }
        this.maxLength = maxLength;
    }

    @Override
    public void onHeaders(Response response)
    {
        Request request = response.getRequest();
        HttpFields headers = response.getHeaders();
        long length = headers.getLongField(HttpHeader.CONTENT_LENGTH);
        if (HttpMethod.HEAD.is(request.getMethod())) {
            length = 0;
        }
        if (length > maxLength) {
            response.abort(new IllegalArgumentException("Buffering capacity " + maxLength + " exceeded"));
        }
    }

    @Override
    public void onContent(Response response, ByteBuffer content)
    {
        int length = content.remaining();
        if (length == 0) {
            return;
        }
        buffer.copyBuffer(content);
    }

    @Override
    public abstract void onComplete(Result result);

    /**
     * @return Content as InputStream
     */
    public InputStream getContentAsInputStream()
    {
        return new ByteBufferInputStream(buffer.takeByteBuffer());
    }
}

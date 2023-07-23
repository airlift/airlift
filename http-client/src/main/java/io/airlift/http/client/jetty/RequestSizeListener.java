package io.airlift.http.client.jetty;

import java.nio.ByteBuffer;

class RequestSizeListener
        implements org.eclipse.jetty.client.api.Request.ContentListener
{
    private long bytes;

    @Override
    public void onContent(org.eclipse.jetty.client.api.Request request, ByteBuffer content)
    {
        bytes += content.remaining();
    }

    public long getBytes()
    {
        return bytes;
    }
}

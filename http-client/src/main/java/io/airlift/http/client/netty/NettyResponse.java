package io.airlift.http.client.netty;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableListMultimap.Builder;
import com.google.common.collect.ListMultimap;
import io.airlift.http.client.Response;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map.Entry;

public class NettyResponse
        implements Response
{
    private final int statusCode;
    private final String statusMessage;
    private final ListMultimap<String, String> headers;
    private final byte[] content;

    public NettyResponse(HttpResponse httpResponse)
    {
        // status
        HttpResponseStatus status = httpResponse.getStatus();
        this.statusCode = status.getCode();
        this.statusMessage = status.getReasonPhrase();

        // headers
        Builder<String, String> headers = ImmutableListMultimap.builder();
        for (Entry<String, String> header : httpResponse.getHeaders()) {
            headers.put(header);
        }
        this.headers = headers.build();

        // content
        ChannelBuffer content = httpResponse.getContent();
        byte[] bytes = new byte[content.readableBytes()];
        content.getBytes(content.readerIndex(), bytes);
        this.content = bytes;
    }

    @Override
    public int getStatusCode()
    {
        return statusCode;
    }

    @Override
    public String getStatusMessage()
    {
        return statusMessage;
    }

    @Override
    public String getHeader(String name)
    {
        List<String> values = headers.get(name);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return null;
    }

    @Override
    public ListMultimap<String, String> getHeaders()
    {
        return headers;
    }

    @Override
    public long getBytesRead()
    {
        return content.length;
    }

    @Override
    public InputStream getInputStream()
            throws IOException
    {
        return new ByteArrayInputStream(content);
    }
}

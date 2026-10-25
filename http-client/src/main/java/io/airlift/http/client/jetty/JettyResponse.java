package io.airlift.http.client.jetty;

import com.google.common.collect.ListMultimap;
import com.google.common.io.CountingInputStream;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpVersion;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseMetadata;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.function.LongSupplier;

import static com.google.common.base.MoreObjects.toStringHelper;

class JettyResponse
        implements Response
{
    private final ResponseMetadata metadata;
    private final Content content;
    private final InputStream inputStream;
    private final LongSupplier bytesRead;

    public JettyResponse(ResponseMetadata metadata, byte[] content)
    {
        this.metadata = metadata;
        this.content = new BytesContent(content);
        this.inputStream = new ByteArrayInputStream(content);
        this.bytesRead = () -> content.length;
    }

    public JettyResponse(ResponseMetadata metadata, InputStream inputStream)
    {
        this.metadata = metadata;
        CountingInputStream countingInputStream = new CountingInputStream(inputStream);
        this.content = new InputStreamContent(countingInputStream);
        this.inputStream = countingInputStream;
        this.bytesRead = countingInputStream::getCount;
    }

    @Override
    public int getStatusCode()
    {
        return metadata.getStatusCode();
    }

    @Override
    public HttpVersion getHttpVersion()
    {
        return metadata.getHttpVersion();
    }

    @Override
    public ListMultimap<HeaderName, String> getHeaders()
    {
        return metadata.getHeaders();
    }

    @Override
    public Content getContent()
    {
        return content;
    }

    @Override
    public InputStream getInputStream()
    {
        return inputStream;
    }

    @Override
    public long getBytesRead()
    {
        return bytesRead.getAsLong();
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("statusCode", getStatusCode())
                .add("headers", getHeaders())
                .toString();
    }
}

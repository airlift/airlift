package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.CountingInputStream;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpVersion;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.http.HttpFields;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.function.LongSupplier;

import static com.google.common.base.MoreObjects.toStringHelper;

class JettyResponse
        implements io.airlift.http.client.Response
{
    private final Response response;
    private final Content content;
    private final InputStream inputStream;
    private final LongSupplier bytesRead;
    private final ListMultimap<HeaderName, String> headers;

    public JettyResponse(Response response, byte[] content)
    {
        this.response = response;
        this.content = new BytesContent(content);
        this.inputStream = new ByteArrayInputStream(content);
        this.bytesRead = () -> content.length;
        this.headers = toHeadersMap(response.getHeaders());
    }

    public JettyResponse(Response response, InputStream inputStream)
    {
        this.response = response;
        CountingInputStream countingInputStream = new CountingInputStream(inputStream);
        this.content = new InputStreamContent(countingInputStream);
        this.inputStream = countingInputStream;
        this.bytesRead = countingInputStream::getCount;
        this.headers = toHeadersMap(response.getHeaders());
    }

    @Override
    public int getStatusCode()
    {
        return response.getStatus();
    }

    @Override
    public HttpVersion getHttpVersion()
    {
        return switch (response.getVersion()) {
            case HTTP_0_9, HTTP_1_0, HTTP_1_1 -> HttpVersion.HTTP_1;
            case HTTP_2 -> HttpVersion.HTTP_2;
            case HTTP_3 -> HttpVersion.HTTP_3;
        };
    }

    @Override
    public ListMultimap<HeaderName, String> getHeaders()
    {
        return headers;
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

    private static ListMultimap<HeaderName, String> toHeadersMap(HttpFields headers)
    {
        ImmutableListMultimap.Builder<HeaderName, String> builder = ImmutableListMultimap.builder();
        for (String name : headers.getFieldNamesCollection()) {
            for (String value : headers.getValuesList(name)) {
                builder.put(HeaderName.of(name), value);
            }
        }
        return builder.build();
    }
}

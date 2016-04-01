package io.airlift.http.client.testing;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.CountingInputStream;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.Response;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

public class TestingResponse
        implements Response
{
    private final HttpStatus status;
    private final ListMultimap<HeaderName, String> headers;
    private final CountingInputStream countingInputStream;

    public TestingResponse(HttpStatus status, ListMultimap<String, String> headers, byte[] bytes)
    {
        this(status, headers, new ByteArrayInputStream(checkNotNull(bytes, "bytes is null")));
    }

    public TestingResponse(HttpStatus status, ListMultimap<String, String> headers, InputStream input)
    {
        this.status = checkNotNull(status, "status is null");
        this.headers = ImmutableListMultimap.copyOf(toHeaderMap(checkNotNull(headers, "headers is null")));
        this.countingInputStream = new CountingInputStream(checkNotNull(input, "input is null"));
    }

    @Override
    public int getStatusCode()
    {
        return status.code();
    }

    @Override
    public String getStatusMessage()
    {
        return status.reason();
    }

    @Override
    public ListMultimap<HeaderName, String> getHeaders()
    {
        return headers;
    }

    @Override
    public long getBytesRead()
    {
        return countingInputStream.getCount();
    }

    @Override
    public InputStream getInputStream()
            throws IOException
    {
        return countingInputStream;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("statusCode", getStatusCode())
                .add("statusMessage", getStatusMessage())
                .add("headers", getHeaders())
                .toString();
    }

    public static ListMultimap<String, String> contentType(MediaType type)
    {
        return ImmutableListMultimap.of(HttpHeaders.CONTENT_TYPE, type.toString());
    }

    public static Response mockResponse(HttpStatus status, MediaType type, String content)
    {
        return new TestingResponse(status, contentType(type), content.getBytes(Charsets.UTF_8));
    }

    private static ListMultimap<HeaderName, String> toHeaderMap(ListMultimap<String, String> headers)
    {
        ImmutableListMultimap.Builder<HeaderName, String> builder = ImmutableListMultimap.builder();
        for (Map.Entry<String, String> entry : headers.entries()) {
            builder.put(HeaderName.of(entry.getKey()), entry.getValue());
        }
        return builder.build();
    }
}

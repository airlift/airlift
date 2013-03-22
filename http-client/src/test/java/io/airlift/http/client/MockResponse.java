package io.airlift.http.client;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.CountingInputStream;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class MockResponse
        implements Response
{
    private final HttpStatus status;
    private final ListMultimap<String, String> headers;
    private final CountingInputStream countingInputStream;

    public MockResponse(HttpStatus status, ListMultimap<String, String> headers, byte[] bytes)
    {
        this(status, headers, new ByteArrayInputStream(checkNotNull(bytes, "bytes is null")));
    }

    public MockResponse(HttpStatus status, ListMultimap<String, String> headers, InputStream input)
    {
        this.status = checkNotNull(status, "status is null");
        this.headers = ImmutableListMultimap.copyOf(checkNotNull(headers, "headers is null"));
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
    public String getHeader(String name)
    {
        List<String> list = getHeaders().get(name);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public ListMultimap<String, String> getHeaders()
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

    public static ListMultimap<String, String> contentType(MediaType type)
    {
        return ImmutableListMultimap.of(HttpHeaders.CONTENT_TYPE, type.toString());
    }

    public static Response mockResponse(HttpStatus status, MediaType type, String content)
    {
        return new MockResponse(status, contentType(type), content.getBytes(Charsets.UTF_8));
    }
}

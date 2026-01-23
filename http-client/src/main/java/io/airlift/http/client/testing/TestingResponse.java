package io.airlift.http.client.testing;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.CountingInputStream;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.HttpVersion;
import io.airlift.http.client.Response;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.LongSupplier;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class TestingResponse
        implements Response
{
    private final HttpStatus status;
    private final ListMultimap<HeaderName, String> headers;
    private final Content content;
    private final InputStream inputStream;
    private final LongSupplier bytesRead;

    public TestingResponse(HttpStatus status, ListMultimap<String, String> headers, byte[] bytes)
    {
        this.status = requireNonNull(status, "status is null");
        this.headers = ImmutableListMultimap.copyOf(toHeaderMap(requireNonNull(headers, "headers is null")));
        this.content = new BytesContent(requireNonNull(bytes, "bytes is null"));
        this.inputStream = new ByteArrayInputStream(bytes);
        this.bytesRead = () -> bytes.length;
    }

    public TestingResponse(HttpStatus status, ListMultimap<String, String> headers, InputStream input)
    {
        this.status = requireNonNull(status, "status is null");
        this.headers = ImmutableListMultimap.copyOf(toHeaderMap(requireNonNull(headers, "headers is null")));
        CountingInputStream countingInputStream = new CountingInputStream(requireNonNull(input, "input is null"));
        this.content = new InputStreamContent(countingInputStream);
        this.inputStream = countingInputStream;
        this.bytesRead = countingInputStream::getCount;
    }

    @Override
    public HttpVersion getHttpVersion()
    {
        return HttpVersion.HTTP_1;
    }

    @Override
    public int getStatusCode()
    {
        return status.code();
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
            throws IOException
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

    public static ListMultimap<String, String> contentType(MediaType type)
    {
        return ImmutableListMultimap.of(HttpHeaders.CONTENT_TYPE, type.toString());
    }

    public static Response mockResponse(HttpStatus status, MediaType type, String content)
    {
        return new TestingResponse(status, contentType(type), content.getBytes(UTF_8));
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

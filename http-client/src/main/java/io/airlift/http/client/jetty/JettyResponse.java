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
    private static final byte[] EMPTY_BODY = new byte[0];

    private final ResponseMetadata metadata;
    private final Content content;
    private final InputStream inputStream;
    private final LongSupplier bytesRead;

    public JettyResponse(ResponseMetadata metadata, ResponseBody body)
    {
        this.metadata = metadata;
        if (body.length() == 0) {
            this.content = new BytesContent(EMPTY_BODY);
            this.inputStream = new ByteArrayInputStream(EMPTY_BODY);
        }
        else if (body.isSingleArray()) {
            byte[] bytes = body.segments().getFirst();
            this.content = new BytesContent(bytes);
            this.inputStream = new ByteArrayInputStream(bytes);
        }
        else {
            SegmentedInputStream segmentedInputStream = new SegmentedInputStream(body);
            this.content = new InputStreamContent(segmentedInputStream);
            this.inputStream = segmentedInputStream;
        }
        this.bytesRead = body::length;
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

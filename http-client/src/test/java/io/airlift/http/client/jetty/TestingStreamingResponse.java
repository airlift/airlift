package io.airlift.http.client.jetty;

import com.google.common.collect.ListMultimap;
import com.google.common.io.Closer;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpVersion;
import io.airlift.http.client.StreamingResponse;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class TestingStreamingResponse
        implements StreamingResponse
{
    private final StreamingResponse delegate;
    private final Closer closer;

    public TestingStreamingResponse(Supplier<StreamingResponse> delegateSupplier, AutoCloseable... closeables)
    {
        closer = Closer.create();
        Stream.of(closeables).forEach(closeable -> closer.register(() -> {
            try {
                closeable.close();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));

        try {
            delegate = delegateSupplier.get();
        }
        catch (Exception e) {
            try {
                closer.close();
            }
            catch (IOException ex) {
                e.addSuppressed(ex);
            }
            throw new RuntimeException(e);
        }

        closer.register(() -> {
            try {
                delegate.close();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public HttpVersion getHttpVersion()
    {
        return delegate.getHttpVersion();
    }

    @Override
    public int getStatusCode()
    {
        return delegate.getStatusCode();
    }

    @Override
    @Nullable
    public String getHeader(String name)
    {
        return delegate.getHeader(name);
    }

    @Override
    public List<String> getHeaders(String name)
    {
        return delegate.getHeaders(name);
    }

    @Override
    public ListMultimap<HeaderName, String> getHeaders()
    {
        return delegate.getHeaders();
    }

    @Override
    public Content getContent()
    {
        return delegate.getContent();
    }

    @Override
    public InputStream getInputStream()
            throws IOException
    {
        return delegate.getInputStream();
    }

    @Override
    public long getBytesRead()
    {
        return delegate.getBytesRead();
    }

    @Override
    public void close()
    {
        try {
            closer.close();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

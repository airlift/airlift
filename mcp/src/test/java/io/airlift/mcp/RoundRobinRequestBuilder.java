package io.airlift.mcp;

import com.google.common.collect.ImmutableList;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static java.util.Objects.requireNonNull;

public class RoundRobinRequestBuilder
        implements HttpRequest.Builder
{
    private static final AtomicInteger counter = new AtomicInteger();

    private final HttpRequest.Builder delegate;
    private final List<URI> uris;
    private final BiConsumer<String, String> headerConsumer;

    public RoundRobinRequestBuilder(HttpRequest.Builder delegate, List<URI> uris, BiConsumer<String, String> headerConsumer)
    {
        this.delegate = requireNonNull(delegate, "delegate is null");
        this.uris = ImmutableList.copyOf(uris);
        this.headerConsumer = requireNonNull(headerConsumer, "headerConsumer is null");
    }

    public static void resetCounter()
    {
        counter.set(0);
    }

    @Override
    public HttpRequest.Builder uri(URI uri)
    {
        int index = Math.abs(counter.getAndIncrement()) % uris.size();
        URI nextUri = uris.get(index);
        delegate.uri(nextUri.resolve(uri.getPath()));
        return this;
    }

    @Override
    public HttpRequest.Builder expectContinue(boolean enable)
    {
        delegate.expectContinue(enable);
        return this;
    }

    @Override
    public HttpRequest.Builder version(HttpClient.Version version)
    {
        delegate.version(version);
        return this;
    }

    @Override
    public HttpRequest.Builder header(String name, String value)
    {
        headerConsumer.accept(name, value);
        delegate.header(name, value);
        return this;
    }

    @Override
    public HttpRequest.Builder headers(String... headers)
    {
        delegate.headers(headers);
        return this;
    }

    @Override
    public HttpRequest.Builder timeout(Duration duration)
    {
        delegate.timeout(duration);
        return this;
    }

    @Override
    public HttpRequest.Builder setHeader(String name, String value)
    {
        headerConsumer.accept(name, value);
        delegate.setHeader(name, value);
        return this;
    }

    @Override
    public HttpRequest.Builder GET()
    {
        delegate.GET();
        return this;
    }

    @Override
    public HttpRequest.Builder POST(HttpRequest.BodyPublisher bodyPublisher)
    {
        delegate.POST(bodyPublisher);
        return this;
    }

    @Override
    public HttpRequest.Builder PUT(HttpRequest.BodyPublisher bodyPublisher)
    {
        delegate.PUT(bodyPublisher);
        return this;
    }

    @Override
    public HttpRequest.Builder DELETE()
    {
        delegate.DELETE();
        return this;
    }

    @Override
    public HttpRequest.Builder method(String method, HttpRequest.BodyPublisher bodyPublisher)
    {
        delegate.method(method, bodyPublisher);
        return this;
    }

    @Override
    public HttpRequest build()
    {
        return delegate.build();
    }

    @Override
    public HttpRequest.Builder copy()
    {
        return new RoundRobinRequestBuilder(delegate.copy(), uris, headerConsumer);
    }

    @Override
    public HttpRequest.Builder HEAD()
    {
        return this;
    }
}

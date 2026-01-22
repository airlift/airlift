package io.airlift.http.client.jetty;

import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;

import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;

class JettyRequestListener
        implements Request.Listener, Response.Listener
{
    private final URI uri;
    private final long created = System.nanoTime();
    private final AtomicLong requestStarted = new AtomicLong();
    private final AtomicLong requestFinished = new AtomicLong();
    private final AtomicLong responseStarted = new AtomicLong();
    private final AtomicLong responseFinished = new AtomicLong();

    public JettyRequestListener(URI uri)
    {
        this.uri = uri;
    }

    public URI getUri()
    {
        return uri;
    }

    public long getCreated()
    {
        return created;
    }

    public long getRequestStarted()
    {
        return requestStarted.get();
    }

    public long getRequestFinished()
    {
        return requestFinished.get();
    }

    public long getResponseStarted()
    {
        return responseStarted.get();
    }

    public long getResponseFinished()
    {
        return responseFinished.get();
    }

    @Override
    public void onBegin(Request request)
    {
        long now = System.nanoTime();
        requestStarted.compareAndSet(0, now);
    }

    @Override
    public void onSuccess(Request request)
    {
        long now = System.nanoTime();
        requestStarted.compareAndSet(0, now);
        requestFinished.compareAndSet(0, now);
    }

    @Override
    public void onBegin(Response response)
    {
        long now = System.nanoTime();
        requestStarted.compareAndSet(0, now);
        requestFinished.compareAndSet(0, now);
        responseStarted.compareAndSet(0, now);
    }

    @Override
    public void onComplete(Result result)
    {
        long now = System.nanoTime();
        requestStarted.compareAndSet(0, now);
        requestFinished.compareAndSet(0, now);
        responseStarted.compareAndSet(0, now);
        responseFinished.compareAndSet(0, now);
    }
}

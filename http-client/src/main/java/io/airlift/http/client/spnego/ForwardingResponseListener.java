package io.airlift.http.client.spnego;

import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.util.Callback;

import java.nio.ByteBuffer;

import static java.util.Objects.requireNonNull;

class ForwardingResponseListener
        implements Response.Listener
{
    private final Response.Listener delegate;

    public ForwardingResponseListener(Response.Listener delegate)
    {
        this.delegate = requireNonNull(delegate, "delegate is null");
    }

    @Override
    public void onBegin(Response response)
    {
        delegate.onBegin(response);
    }

    @Override
    public boolean onHeader(Response response, HttpField field)
    {
        return delegate.onHeader(response, field);
    }

    @Override
    public void onHeaders(Response response)
    {
        delegate.onHeaders(response);
    }

    @Override
    public void onContent(Response response, ByteBuffer content)
    {
        delegate.onContent(response, content);
    }

    @Override
    public void onContent(Response response, ByteBuffer content, Callback callback)
    {
        delegate.onContent(response, content, callback);
    }

    @Override
    public void onSuccess(Response response)
    {
        delegate.onSuccess(response);
    }

    @Override
    public void onFailure(Response response, Throwable failure)
    {
        delegate.onFailure(response, failure);
    }

    @Override
    public void onComplete(Result result)
    {
        delegate.onComplete(result);
    }
}

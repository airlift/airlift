package io.airlift.http.client.jetty;

import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.client.RetainingResponseListener;

import static java.util.Objects.requireNonNull;

class JettyResponseListener<T, E extends Exception>
        extends RetainingResponseListener
{
    private final Request request;
    private final JettyResponseFuture<T, E> future;

    public JettyResponseListener(Request request, JettyResponseFuture<T, E> future, int maxLength)
    {
        super(maxLength);
        this.future = requireNonNull(future, "future is null");
        this.request = requireNonNull(request, "request is null");
    }

    public JettyResponseFuture<T, E> send()
    {
        request.send(this);
        return future;
    }

    @Override
    public void onComplete(Result result)
    {
        if (result.isFailed()) {
            future.failed(result.getFailure());
        }
        else {
            future.completed(result.getResponse(), getContentAsInputStream());
        }
    }
}

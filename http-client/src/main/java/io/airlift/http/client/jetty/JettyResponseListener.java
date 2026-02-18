package io.airlift.http.client.jetty;

import org.eclipse.jetty.client.BufferingResponseListener;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Result;

import static java.util.Objects.requireNonNull;

class JettyResponseListener<T, E extends Exception>
        // TODO use RetainingResponseListener to reduce data copying?
        extends BufferingResponseListener
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
            // TODO it would be better to return response data as InputStream based on Jetty chunks, without any data copying
            // There is no such builtin Jetty response listener to use, see https://github.com/jetty/jetty.project/issues/14373.
            // Doing so would also require removal of data buffering in e.g. `JsonResponseHandler` which is used for error reporting
            // or using new StreamingJsonResponseHandler that decodes JSON directly from the Response InputStream.
            // Perhaps buffering for errors could be done only on retries?
            // Once Jetty 12.1.7 is released https://github.com/jetty/jetty.project/commit/78780b988954bb73c061ec3719059a2a87c371de
            // can be leveraged to avoid copying data by calling takeContentAsInputStream()
            future.completed(result.getResponse(), getContentAsInputStream());
        }
    }
}

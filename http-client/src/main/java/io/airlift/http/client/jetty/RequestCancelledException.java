package io.airlift.http.client.jetty;

final class RequestCancelledException
        extends Throwable
{
    static final RequestCancelledException INSTANCE = new RequestCancelledException();

    private RequestCancelledException()
    {
        super(
                "Request was cancelled",
                /* cause */ null,
                /* enable suppression */ false,
                /* writable stack trace */ false);
    }
}

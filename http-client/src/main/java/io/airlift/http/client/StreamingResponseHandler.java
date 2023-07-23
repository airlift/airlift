package io.airlift.http.client;

import static io.airlift.http.client.ResponseHandlerUtils.propagate;

public interface StreamingResponseHandler<T>
        extends ResponseHandler<StreamingResponseIterator<T>, RuntimeException>
{
    @Override
    default StreamingResponseIterator<T> handleException(Request request, Exception exception)
            throws RuntimeException
    {
        throw propagate(request, exception);
    }

    @Override
    default StreamingResponseIterator<T> handle(Request request, Response response)
            throws RuntimeException
    {
        if (response instanceof StreamingResponse streamingResponse) {
            return handle(request, streamingResponse);
        }
        throw new IllegalArgumentException("Response must be a StreamingResponse");
    }

    StreamingResponseIterator<T> handle(Request request, StreamingResponse response);
}

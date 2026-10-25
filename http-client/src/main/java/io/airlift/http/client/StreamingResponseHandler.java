package io.airlift.http.client;

import java.nio.ByteBuffer;

/**
 * Handles a response body as it arrives, without buffering it in the client.
 */
public interface StreamingResponseHandler<T, E extends Exception>
{
    /**
     * Called when the request or response fails, but not for failures thrown by this handler or its body consumer.
     */
    T handleException(Request request, Exception exception)
            throws E;

    /**
     * Called when the response headers arrive. The returned consumer receives the body.
     */
    BodyConsumer<T, E> handleHeaders(Request request, ResponseMetadata response)
            throws E;

    interface BodyConsumer<T, E extends Exception>
    {
        /**
         * Consumes the next part of the body. The content is only valid until this method returns.
         */
        void accept(ByteBuffer content)
                throws E;

        /**
         * Called after the whole body has been consumed.
         */
        T complete()
                throws E;
    }
}

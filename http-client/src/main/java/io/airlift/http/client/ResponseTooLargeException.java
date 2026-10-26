package io.airlift.http.client;

import java.io.IOException;

/**
 * Signals that a response body exceeds the maximum response content length.
 */
public final class ResponseTooLargeException
        extends IOException
{
    public ResponseTooLargeException(String message)
    {
        super(message);
    }
}

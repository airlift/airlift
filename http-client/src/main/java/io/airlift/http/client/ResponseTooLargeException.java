package io.airlift.http.client;

public class ResponseTooLargeException
        extends RuntimeException
{
    public ResponseTooLargeException()
    {
        super("Maximum response size exceeded");
    }
}

package com.proofpoint.http.client;

public class ResponseTooLargeException
        extends RuntimeException
{
    public ResponseTooLargeException()
    {
        super("Maximum response size exceeded");
    }
}

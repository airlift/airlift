package io.airlift.http.client.netty;

public class UnknownRequestException
        extends Exception
{
    public UnknownRequestException()
    {
        // yes this is vague, we use this when we have no information
        super("Request had some unknown problem");
    }
}

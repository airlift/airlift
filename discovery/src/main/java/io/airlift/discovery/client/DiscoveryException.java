package io.airlift.discovery.client;

public class DiscoveryException extends RuntimeException
{
    public DiscoveryException()
    {
    }

    public DiscoveryException(String s)
    {
        super(s);
    }

    public DiscoveryException(String s, Throwable throwable)
    {
        super(s, throwable);
    }

    public DiscoveryException(Throwable throwable)
    {
        super(throwable);
    }
}

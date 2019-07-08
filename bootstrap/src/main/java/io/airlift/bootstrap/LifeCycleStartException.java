package io.airlift.bootstrap;

public class LifeCycleStartException
        extends RuntimeException
{
    public LifeCycleStartException(String message)
    {
        super(message);
    }

    public LifeCycleStartException(String message, Throwable cause)
    {
        super(message, cause);
    }
}

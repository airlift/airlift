package io.airlift.bootstrap;

public class LifeCycleStopException
        extends RuntimeException
{
    public LifeCycleStopException()
    {
        super("Exceptions occurred during lifecycle stop");
    }
}

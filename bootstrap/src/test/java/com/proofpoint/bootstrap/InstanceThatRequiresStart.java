package com.proofpoint.bootstrap;

import javax.annotation.PostConstruct;

public class InstanceThatRequiresStart
{
    private boolean hasStarted = false;

    @PostConstruct
    public void start()
    {
        hasStarted = true;
    }

    public void doSomething()
    {
        if (!hasStarted) {
            throw new IllegalStateException();
        }
    }
}

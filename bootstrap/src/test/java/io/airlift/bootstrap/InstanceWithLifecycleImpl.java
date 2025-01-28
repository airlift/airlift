package io.airlift.bootstrap;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.concurrent.atomic.AtomicBoolean;

public class InstanceWithLifecycleImpl
        implements InstanceWithLifecycle
{
    private final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public boolean isStarted()
    {
        return started.get();
    }

    @PostConstruct
    public void startMeUp()
    {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Already started");
        }
    }

    @PreDestroy
    public void shutMeDown()
    {
        if (!started.compareAndSet(true, false)) {
            throw new IllegalStateException("Not started");
        }
    }
}

package io.airlift.event.client;

import io.airlift.units.Duration;

public interface FlushableEventClient
        extends EventClient
{
    boolean flush(Duration timeout)
            throws InterruptedException;
}
